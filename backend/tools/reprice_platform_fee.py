#!/usr/bin/env python3
"""One-time, audited 10% platform-fee correction. Dry-run by default.

Run on the pinned zhyq production host only, after V125 and the deployment backup.
No credentials are written or printed. Paid money is never rewritten.
"""
import argparse
import copy
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path
import re
import subprocess

D = Decimal
RATE = D('0.10')
MILL = D('0.001')
CENT = D('0.01')
RUN_KEY = 'platform-fee-10-all-history-20261123'
TENANT_ID = 1
FIELDS = {
    'crm_customer_pricing': ['id', 'customer_id', 'contract_id', 'owner_promoter_id', 'total_per_order', 'owner_per_order', 'beneficiaries_json', 'version'],
    'crm_referral_order': ['id', 'source_type', 'pricing_id', 'commission_unit_price', 'commission_order_count', 'company_per_order', 'pool_amount', 'status', 'version'],
    'crm_promoter_commission': ['id', 'referral_order_id', 'promoter_id', 'pricing_id', 'amount_per_order', 'order_count', 'amount', 'sign', 'status', 'adjustment_sequence', 'settle_batch_id', 'withdrawal_id', 'withdrawn_amount', 'withdrawal_amount', 'version'],
    'crm_settle_batch': ['id', 'cnt', 'total_amount', 'version'],
    'crm_withdrawal': ['id', 'promoter_id', 'amount', 'tax_mode', 'tax_amount', 'net_amount', 'status', 'commission_ids', 'commission_allocations', 'version'],
}
FIELDS = {table: fields + ['tenant_id'] for table, fields in FIELDS.items()}


class GuardError(RuntimeError):
    pass


def dec(value):
    return D('0') if value is None else D(str(value))


def json_exact(value):
    if isinstance(value, D):
        return format(value, 'f')
    if isinstance(value, dict):
        return '{' + ','.join(json.dumps(str(k), ensure_ascii=False) + ':' + json_exact(v) for k, v in value.items()) + '}'
    if isinstance(value, (list, tuple)):
        return '[' + ','.join(json_exact(v) for v in value) + ']'
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def parse_json(value):
    return json.loads(value, parse_float=D) if isinstance(value, str) else value


def scaled_rates(pricing):
    """Keep allocations under 90%; proportionally shrink over-budget plans in mills.

    Largest remainder distributes any whole-mill residue deterministically. The
    sub-mill residue remains unallocated, not an invented extra platform fee.
    """
    total = dec(pricing['total_per_order'])
    owner = pricing['owner_promoter_id']
    items = parse_json(pricing['beneficiaries_json']) or []
    rates = {owner: dec(pricing['owner_per_order'])}
    for item in items:
        pid = item['promoterId']
        if pid in rates:
            raise GuardError('duplicate pricing beneficiary: ' + str(pricing['id']))
        rates[pid] = dec(item['amountPerOrder'])
    if total < 0 or any(v < 0 or v != v.quantize(MILL) for v in rates.values()):
        raise GuardError('invalid pricing money: ' + str(pricing['id']))
    allocated = sum(rates.values(), D('0'))
    maximum = (total * (1 - RATE)).quantize(MILL, rounding=ROUND_DOWN)
    if allocated <= maximum:
        return rates, rates.copy(), items
    if allocated == 0:
        raise GuardError('invalid zero allocation')
    target = int(maximum / MILL)
    weights = {pid: int(amount / MILL) for pid, amount in rates.items()}
    denominator = sum(weights.values())
    quotients = {pid: divmod(target * weight, denominator) for pid, weight in weights.items()}
    result = {pid: q[0] for pid, q in quotients.items()}
    remainder = target - sum(result.values())
    ranking = sorted(rates, key=lambda pid: (-quotients[pid][1], 0 if pid == owner else 1, pid))
    for pid in ranking[:remainder]:
        result[pid] += 1
    return rates, {pid: value * MILL for pid, value in result.items()}, items


def make_plan(data, tax_rate=D('0.20')):
    if any(row.get('tenant_id') != TENANT_ID for rows in data.values() for row in rows):
        raise GuardError('repricing is restricted to tenant 1')
    original = copy.deepcopy(data)
    after = copy.deepcopy(data)
    maps = {table: {row['id']: row for row in rows} for table, rows in after.items()}
    old_maps = {table: {row['id']: row for row in rows} for table, rows in original.items()}
    rates = {}
    over_budget = 0
    for p in after['crm_customer_pricing']:
        old_rates, new_rates, beneficiaries = scaled_rates(p)
        rates[p['id']] = (old_rates, new_rates)
        if old_rates != new_rates:
            over_budget += 1
            p['owner_per_order'] = new_rates[p['owner_promoter_id']]
            for beneficiary in beneficiaries:
                beneficiary['amountPerOrder'] = new_rates[beneficiary['promoterId']]
            p['beneficiaries_json'] = json_exact(beneficiaries)
    target_orders = {o['id']: o for o in after['crm_referral_order'] if o['source_type'] == 2 and o['pricing_id'] is not None}
    commissions_by_order = {}
    for c in after['crm_promoter_commission']:
        commissions_by_order.setdefault(c['referral_order_id'], []).append(c)
    changed_commissions = set()
    paid_ids = set()
    for oid, order in target_orders.items():
        p = maps['crm_customer_pricing'].get(order['pricing_id'])
        if p is None or dec(p['total_per_order']) != dec(order['commission_unit_price']):
            raise GuardError('pricing/order unit mismatch: ' + str(oid))
        count = order['commission_order_count']
        if not isinstance(count, int) or count < 1:
            raise GuardError('invalid order count: ' + str(oid))
        old_rates, new_rates = rates[p['id']]
        original_rows = [c for c in commissions_by_order.get(oid, []) if c['sign'] == 1 and not c.get('adjustment_sequence')]
        if len({c['promoter_id'] for c in original_rows}) != len(original_rows):
            raise GuardError('duplicate original commission: ' + str(oid))
        if sum((dec(c['amount']) for c in original_rows), D('0')) != dec(order['pool_amount']):
            raise GuardError('original commission pool mismatch: ' + str(oid))
        if sum(new_rates.values(), D('0')) > dec(order['commission_unit_price']) * (1 - RATE):
            raise GuardError('allocation above 90%')
        for c in commissions_by_order.get(oid, []):
            pid = c['promoter_id']
            if pid not in old_rates or dec(c['amount_per_order']) != old_rates[pid] or c['order_count'] != count:
                raise GuardError('commission snapshot mismatch: ' + str(c['id']))
            expected_old = old_rates[pid] * count * c['sign']
            if dec(c['amount']) != expected_old:
                raise GuardError('commission amount mismatch: ' + str(c['id']))
            amount = new_rates[pid] * count * c['sign']
            if amount != dec(c['amount']):
                withdrawal = maps['crm_withdrawal'].get(c['withdrawal_id'])
                if c['withdrawal_id'] is not None and withdrawal is None:
                    raise GuardError('missing linked withdrawal: ' + str(c['withdrawal_id']))
                if dec(c['withdrawn_amount']) != 0 or c['status'] == 4 or (withdrawal and withdrawal['status'] == 3):
                    paid_ids.add(c['id'])
                changed_commissions.add(c['id'])
            c['amount_per_order'] = new_rates[pid]
            c['amount'] = amount
        total = dec(order['commission_unit_price'])
        allocated = sum(new_rates.values(), D('0'))
        order['pool_amount'] = allocated * count
        order['company_per_order'] = total - allocated
        order['platform_service_fee_per_order'] = (total * RATE).quantize(D('0.0001'))
        order['unallocated_per_order'] = total - allocated - order['platform_service_fee_per_order']
        if order['pool_amount'] + (order['platform_service_fee_per_order'] + order['unallocated_per_order']) * count != total * count:
            raise GuardError('order money conservation failed')
        if sum((dec(c['amount']) for c in original_rows), D('0')) != order['pool_amount']:
            raise GuardError('new commission pool mismatch: ' + str(oid))
    if paid_ids:
        raise GuardError('Paid commissions require an adjustment ledger; no payment facts were changed. Commission IDs: ' + ','.join(map(str, sorted(paid_ids))))
    changed_batches = {c['settle_batch_id'] for c in after['crm_promoter_commission'] if c['id'] in changed_commissions and c['settle_batch_id'] is not None}
    for bid in changed_batches:
        batch = maps['crm_settle_batch'].get(bid)
        if batch is None:
            raise GuardError('missing settlement batch: ' + str(bid))
        before_members = [c for c in original['crm_promoter_commission'] if c['settle_batch_id'] == bid]
        members = [c for c in after['crm_promoter_commission'] if c['settle_batch_id'] == bid]
        if dec(batch['total_amount']) != sum((dec(c['amount']) for c in before_members), D('0')) or batch['cnt'] != len(members):
            raise GuardError('settlement batch baseline mismatch: ' + str(bid))
        batch['total_amount'] = sum((dec(c['amount']) for c in members), D('0'))
    changed_withdrawals = set()
    for w in after['crm_withdrawal']:
        members = [c for c in after['crm_promoter_commission'] if c['withdrawal_id'] == w['id']]
        if not any(c['id'] in changed_commissions for c in members):
            continue
        if w['status'] not in (1, 2):
            raise GuardError('unexpected linked withdrawal status: ' + str(w['id']))
        before_members = [old_maps['crm_promoter_commission'][c['id']] for c in members]
        if set(parse_json(w['commission_ids']) or []) != {c['id'] for c in members}:
            raise GuardError('withdrawal commission IDs mismatch: ' + str(w['id']))
        old_reservations = {c['id']: dec(c['withdrawal_amount']) if c['withdrawal_amount'] is not None else dec(c['amount']) - dec(c['withdrawn_amount']) for c in before_members}
        if sum(old_reservations.values(), D('0')) != dec(w['amount']):
            raise GuardError('withdrawal reservation baseline mismatch: ' + str(w['id']))
        if not (0 <= tax_rate <= 1):
            raise GuardError('invalid tax rate')
        if w['tax_mode'] == 1 and (dec(w['amount']) * tax_rate).quantize(CENT, rounding=ROUND_HALF_UP) != dec(w['tax_amount']):
            raise GuardError('withdrawal tax policy changed: ' + str(w['id']))
        reservations = {}
        for c in members:
            old = old_maps['crm_promoter_commission'][c['id']]
            reserve = old_reservations[c['id']]
            reservations[c['id']] = reserve if old['amount'] == c['amount'] else (reserve * dec(c['amount']) / dec(old['amount'])).quantize(MILL, rounding=ROUND_DOWN)
        amount = sum(reservations.values(), D('0')).quantize(CENT, rounding=ROUND_DOWN)
        if amount < CENT:
            raise GuardError('pending withdrawal falls below one cent; explicitly cancel before repricing: ' + str(w['id']))
        excess = sum(reservations.values(), D('0')) - amount
        for cid in sorted(reservations, reverse=True):
            if excess <= 0:
                break
            if reservations[cid] > 0:
                trim = min(reservations[cid], excess)
                reservations[cid] -= trim
                excess -= trim
        if excess != 0 or amount > dec(w['amount']):
            raise GuardError('withdrawal conservation failed')
        for c in members:
            c['withdrawal_amount'] = reservations[c['id']]
        w['amount'] = amount
        w['tax_amount'] = (amount * tax_rate).quantize(CENT, rounding=ROUND_HALF_UP) if w['tax_mode'] == 1 else D('0.00')
        w['net_amount'] = amount - w['tax_amount']
        w['commission_allocations'] = json_exact([{'commissionId': c['id'], 'amount': reservations[c['id']]} for c in sorted(members, key=lambda c: c['id'])])
        changed_withdrawals.add(w['id'])
    changes = []
    for table, rows in after.items():
        for row in rows:
            old = old_maps[table][row['id']]
            changed = {key: value for key, value in row.items() if key not in ('id', 'version') and value != old.get(key)}
            if changed:
                changes.append({'table': table, 'id': row['id'], 'version': old['version'], 'before': old, 'after': row, 'set': changed})
    before_pool = sum((dec(old_maps['crm_referral_order'][oid]['pool_amount']) for oid in target_orders), D('0'))
    after_pool = sum((dec(o['pool_amount']) for o in target_orders.values()), D('0'))
    summary = {'runKey': RUN_KEY, 'tenantId': TENANT_ID, 'pricingVersions': len(rates), 'overBudgetVersions': over_budget,
               'orders': len(target_orders), 'orderCount': sum(o['commission_order_count'] for o in target_orders.values()),
               'totalCommission': sum((dec(o['commission_unit_price']) * o['commission_order_count'] for o in target_orders.values()), D('0')),
               'beforePersonalCommission': before_pool, 'afterPersonalCommission': after_pool, 'personalReduction': before_pool - after_pool,
               'platformServiceFee': sum((o['platform_service_fee_per_order'] * o['commission_order_count'] for o in target_orders.values()), D('0')),
               'unallocated': sum((o['unallocated_per_order'] * o['commission_order_count'] for o in target_orders.values()), D('0')),
               'changedCommissionRows': len(changed_commissions), 'changedSettlementBatches': len(changed_batches),
               'changedPendingWithdrawals': len(changed_withdrawals), 'changedRows': len(changes)}
    summary['planSha256'] = hashlib.sha256(json_exact(changes).encode()).hexdigest()
    return changes, summary, after


def sql_value(value):
    if value is None:
        return 'NULL'
    if isinstance(value, (int, D)):
        return str(value)
    return "CONVERT(0x" + str(value).encode('utf-8').hex() + " USING utf8mb4)"


class Mysql:
    def __init__(self):
        self.process = subprocess.Popen(['docker', 'exec', '-i', 'zhyq-mysql', 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names --unbuffered zhyq_park'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8')

    def sql(self, query):
        self.process.stdin.write(query.rstrip(';') + ";\nSELECT '__fee_reprice_done__';\n")
        self.process.stdin.flush()
        rows = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise GuardError('MySQL connection ended; transaction was not committed')
            line = line.rstrip('\n')
            if line == '__fee_reprice_done__':
                return rows
            if line.startswith('ERROR'):
                raise GuardError(line)
            rows.append(line)

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            self.process.wait(timeout=10)


def load_data(db, apply):
    data = {}
    for table, fields in FIELDS.items():
        fields = fields.copy()
        if table == 'crm_referral_order':
            present = db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='crm_referral_order' AND column_name='platform_service_fee_per_order'")
            if present == ['1']:
                fields += ['platform_service_fee_per_order', 'unallocated_per_order']
            elif apply:
                raise GuardError('V125 must be deployed before applying')
        expression = ','.join("'" + f + "',`" + f + '`' for f in fields)
        where = 'deleted=0 AND tenant_id=' + str(TENANT_ID)
        if table == 'crm_referral_order':
            where += ' AND source_type=2 AND pricing_id IS NOT NULL'
        rows = db.sql('SELECT JSON_OBJECT(' + expression + ') FROM ' + table + ' WHERE ' + where + ' ORDER BY id' + (' FOR UPDATE' if apply else ''))
        data[table] = [json.loads(row, parse_float=D) for row in rows]
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--expected-commit')
    parser.add_argument('--expected-plan-sha256')
    args = parser.parse_args()
    if args.apply:
        if not args.expected_commit or not re.fullmatch('[0-9a-f]{40}', args.expected_commit):
            raise GuardError('apply requires the verified full release commit')
        if not args.expected_plan_sha256 or not re.fullmatch('[0-9a-f]{64}', args.expected_plan_sha256):
            raise GuardError('apply requires a reviewed dry-run plan SHA256')
        checkout = subprocess.check_output(['git', '-C', '/opt/zhyq', 'rev-parse', 'HEAD'], text=True).strip()
        if checkout != args.expected_commit or Path('/opt/zhyq/.sentinel-running-commit').read_text().strip() != checkout:
            raise GuardError('source and verified runtime receipt do not match target')
    db = Mysql()
    committed = False
    try:
        if db.sql("SELECT GET_LOCK('zhyq-platform-fee-10-reprice',0)") != ['1']:
            raise GuardError('another repricing operation is running')
        db.sql('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE' if args.apply else 'SET TRANSACTION READ ONLY')
        db.sql('START TRANSACTION')
        run_table = db.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='crm_platform_fee_reprice_run'") == ['1']
        if run_table and db.sql('SELECT run_key FROM crm_platform_fee_reprice_run WHERE tenant_id=' + str(TENANT_ID) + ' AND run_key=' + sql_value(RUN_KEY)):
            print(json_exact({'status': 'already_applied', 'runKey': RUN_KEY}))
            db.sql('ROLLBACK')
            return
        if args.apply and not run_table:
            raise GuardError('V125 audit tables are missing')
        data = load_data(db, args.apply)
        tax_rows = db.sql("SELECT svalue FROM biz_setting WHERE module='marketing' AND skey='tax_rate' AND deleted=0 AND tenant_id=" + str(TENANT_ID) + " LIMIT 1")
        tax_rate = D(tax_rows[0]) if tax_rows and tax_rows[0].strip() else D('0.20')
        changes, summary, after = make_plan(data, tax_rate)
        print(json_exact({'status': 'planned', **summary}), flush=True)
        if not args.apply:
            db.sql('ROLLBACK')
            return
        if summary['planSha256'] != args.expected_plan_sha256:
            raise GuardError('ledger changed since reviewed dry-run; no changes applied')
        db.sql('INSERT INTO crm_platform_fee_reprice_run(tenant_id,run_key,summary_json) VALUES (' + str(TENANT_ID) + ',' + sql_value(RUN_KEY) + ',' + sql_value(json_exact(summary)) + ')')
        for change in changes:
            audited_after = {**change['after'], 'version': change['version'] + 1}
            db.sql('INSERT INTO crm_platform_fee_reprice_audit(tenant_id,run_key,table_name,row_id,before_json,after_json) VALUES ('
                   + ','.join(sql_value(v) for v in [TENANT_ID, RUN_KEY, change['table'], change['id'], json_exact(change['before']), json_exact(audited_after)]) + ')')
            assignments = [key + '=' + sql_value(value) for key, value in change['set'].items()]
            assignments += ['version=version+1', "update_by='fee-10-reprice'", 'update_time=NOW()']
            result = db.sql('UPDATE ' + change['table'] + ' SET ' + ','.join(assignments) + ' WHERE id=' + str(change['id']) + ' AND version=' + str(change['version']) + ' AND deleted=0 AND tenant_id=' + str(TENANT_ID) + '; SELECT ROW_COUNT()')
            if result != ['1']:
                raise GuardError('concurrent row change; rolling back')
        verify_changes, verified, _ = make_plan(load_data(db, True), tax_rate)
        if verify_changes or verified['orderCount'] != summary['orderCount'] or verified['totalCommission'] != summary['totalCommission'] or verified['afterPersonalCommission'] != summary['afterPersonalCommission']:
            raise GuardError('post-update invariants failed; rolling back')
        db.sql('COMMIT')
        committed = True
        print(json_exact({'status': 'applied_and_verified', **summary}), flush=True)
    finally:
        if not committed and db.process.poll() is None:
            try:
                db.sql('ROLLBACK')
            except Exception:
                pass
        db.close()


if __name__ == '__main__':
    main()
