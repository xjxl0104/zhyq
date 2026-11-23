import copy
from decimal import Decimal as D
import os
import subprocess
import unittest
from reprice_platform_fee import GuardError, Mysql, RUN_KEY, json_exact, make_plan, scaled_rates, sql_value


def fixture():
    data = {
        'crm_customer_pricing': [dict(id=1, customer_id=10, contract_id=20, owner_promoter_id=4,
                                     total_per_order=D('.020'), owner_per_order=D('.019'), beneficiaries_json='[]', version=1)],
        'crm_referral_order': [dict(id=2, source_type=2, pricing_id=1, commission_unit_price=D('.020'),
                                   commission_order_count=100, company_per_order=D('.001'), pool_amount=D('1.900'), status=2, version=1)],
        'crm_promoter_commission': [dict(id=3, referral_order_id=2, promoter_id=4, pricing_id=1, amount_per_order=D('.019'), order_count=100,
                                        amount=D('1.900'), sign=1, status=3, adjustment_sequence=0, settle_batch_id=5,
                                        withdrawal_id=None, withdrawn_amount=D('0'), withdrawal_amount=None, version=1)],
        'crm_settle_batch': [dict(id=5, cnt=1, total_amount=D('1.900'), version=1)],
        'crm_withdrawal': [],
    }
    for rows in data.values():
        for row in rows:
            row['tenant_id'] = 1
    return data


class RepriceTest(unittest.TestCase):
    def test_total_count_and_unit_price_unchanged_and_all_buckets_balance(self):
        data = fixture()
        original = copy.deepcopy(data)
        changes, summary, after = make_plan(data)
        self.assertEqual(data, original)
        self.assertEqual(summary['personalReduction'], D('.100'))
        self.assertEqual(summary['totalCommission'], D('2.000'))
        self.assertEqual(summary['platformServiceFee'], D('.200'))
        self.assertEqual(after['crm_referral_order'][0]['commission_order_count'], 100)
        self.assertEqual(after['crm_referral_order'][0]['commission_unit_price'], D('.020'))
        self.assertEqual(after['crm_promoter_commission'][0]['amount'], D('1.800'))
        self.assertEqual(after['crm_settle_batch'][0]['total_amount'], D('1.800'))
        self.assertEqual(len(changes), 4)

    def test_replanning_the_updated_dataset_is_idempotent(self):
        _, _, after = make_plan(fixture())
        changes, summary, _ = make_plan(after)
        self.assertEqual(changes, [])
        self.assertEqual(summary['personalReduction'], 0)

    def test_already_paid_money_is_never_rewritten(self):
        data = fixture()
        data['crm_promoter_commission'][0]['withdrawn_amount'] = D('1.00')
        with self.assertRaisesRegex(GuardError, 'Paid commissions'):
            make_plan(data)

    def test_pending_withdrawal_and_its_reservations_and_tax_are_synchronized(self):
        data = fixture()
        c = data['crm_promoter_commission'][0]
        c['withdrawal_id'] = 6
        c['withdrawal_amount'] = D('1.900')
        data['crm_withdrawal'] = [dict(id=6, promoter_id=4, amount=D('1.90'), tax_mode=1, tax_amount=D('.38'),
            net_amount=D('1.52'), status=1, commission_ids='[3]', commission_allocations='[{"commissionId":3,"amount":1.9}]', version=1, tenant_id=1)]
        _, summary, after = make_plan(data)
        self.assertEqual(summary['changedPendingWithdrawals'], 1)
        self.assertEqual(after['crm_withdrawal'][0]['amount'], D('1.80'))
        self.assertEqual(after['crm_withdrawal'][0]['tax_amount'], D('.36'))
        self.assertEqual(after['crm_withdrawal'][0]['net_amount'], D('1.44'))
        self.assertEqual(after['crm_promoter_commission'][0]['withdrawal_amount'], D('1.800'))

    def test_proportional_rounding_conserves_mills_without_inventing_fee(self):
        p = dict(id=1, owner_promoter_id=4, total_per_order=D('.005'), owner_per_order=D('.003'),
                 beneficiaries_json='[{"promoterId":2,"amountPerOrder":0.002}]')
        _, rates, _ = scaled_rates(p)
        self.assertEqual(rates, {4: D('.002'), 2: D('.002')})
        self.assertEqual(D('.005') - sum(rates.values()) - D('.0005'), D('.0005'))

    def test_under_budget_personal_rates_are_unchanged(self):
        p = dict(id=1, owner_promoter_id=4, total_per_order=D('.100'), owner_per_order=D('.050'),
                 beneficiaries_json='[{"promoterId":2,"amountPerOrder":0.02}]')
        before, after, _ = scaled_rates(p)
        self.assertEqual(before, after)

    def test_zero_owner_is_preserved_and_beneficiary_takes_its_proportional_reduction(self):
        p = dict(id=1, owner_promoter_id=4, total_per_order=D('.020'), owner_per_order=D('0'),
                 beneficiaries_json='[{"promoterId":2,"amountPerOrder":0.019}]')
        _, after, _ = scaled_rates(p)
        self.assertEqual(after, {4: D('0'), 2: D('.018')})

    def test_bad_order_snapshot_stops_before_any_changes(self):
        data = fixture()
        data['crm_referral_order'][0]['commission_unit_price'] = D('.1')
        with self.assertRaisesRegex(GuardError, 'unit mismatch'):
            make_plan(data)

    def test_decimal_json_preserves_money_without_float_rounding(self):
        self.assertEqual(json_exact({'fee': D('.0001'), 'amount': D('99999999999.999')}), '{"fee":0.0001,"amount":99999999999.999}')

    def test_negative_refund_tracks_same_new_unit_and_preserves_net_zero(self):
        data = fixture()
        refund = copy.deepcopy(data['crm_promoter_commission'][0])
        refund.update(id=7, sign=-1, amount=D('-1.900'), status=2, settle_batch_id=None)
        data['crm_promoter_commission'].append(refund)
        _, _, after = make_plan(data)
        self.assertEqual(sum(c['amount'] for c in after['crm_promoter_commission']), 0)
        self.assertEqual(after['crm_promoter_commission'][1]['amount_per_order'], D('.018'))

    def test_partial_pending_withdrawal_rounds_to_cents_and_retains_unreserved_mills(self):
        data = fixture()
        c = data['crm_promoter_commission'][0]
        c['withdrawal_id'] = 6
        c['withdrawal_amount'] = D('1.000')
        data['crm_withdrawal'] = [dict(id=6, promoter_id=4, amount=D('1.00'), tax_mode=1, tax_amount=D('.20'),
            net_amount=D('.80'), status=2, commission_ids='[3]', commission_allocations='[{"commissionId":3,"amount":1}]', version=1, tenant_id=1)]
        _, _, after = make_plan(data)
        self.assertEqual(after['crm_withdrawal'][0]['amount'], D('.94'))
        self.assertEqual(after['crm_promoter_commission'][0]['withdrawal_amount'], D('.940'))
        self.assertEqual(after['crm_promoter_commission'][0]['amount'], D('1.800'))
        self.assertEqual(make_plan(after)[0], [])

    def test_other_tenant_data_is_refused_before_planning(self):
        data = fixture()
        data['crm_customer_pricing'][0]['tenant_id'] = 2
        with self.assertRaisesRegex(GuardError, 'tenant 1'):
            make_plan(data)

    def test_voided_batch_member_retains_batch_membership_and_count(self):
        data = fixture()
        data['crm_promoter_commission'][0]['status'] = 5
        _, _, after = make_plan(data)
        self.assertEqual(after['crm_promoter_commission'][0]['status'], 5)
        self.assertEqual(after['crm_settle_batch'][0]['cnt'], 1)
        self.assertEqual(after['crm_settle_batch'][0]['total_amount'], D('1.800'))


@unittest.skipUnless(os.environ.get('REPRICE_TEST_MYSQL_DEFAULTS'),
                     'set REPRICE_TEST_MYSQL_DEFAULTS for isolated localhost MySQL regression')
class MysqlCollationTest(unittest.TestCase):
    """Real MySQL regression; only connection-local temporary tables are changed."""

    def setUp(self):
        self.db = Mysql.__new__(Mysql)
        self.db.process = subprocess.Popen([
            os.environ.get('REPRICE_TEST_MYSQL_EXECUTABLE', 'mysql'),
            '--defaults-extra-file=' + os.environ['REPRICE_TEST_MYSQL_DEFAULTS'],
            '--host=127.0.0.1', '--port=13326', '--protocol=TCP',
            '--default-character-set=utf8mb4', '--batch', '--raw',
            '--skip-column-names', '--unbuffered', 'warehouse_http_it'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8')
        self.addCleanup(self.db.close)
        self.db.sql('CREATE TEMPORARY TABLE fee_reprice_collation_it ('
                    'run_key VARCHAR(64) COLLATE utf8mb4_unicode_ci PRIMARY KEY, '
                    'summary_json JSON) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')

    def test_set_names_alone_does_not_repair_mysql8_convert_comparison(self):
        self.db.sql('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci')
        self.assertEqual(self.db.sql('SELECT COLLATION(CONVERT(0x616263 USING utf8mb4))'),
                         ['utf8mb4_0900_ai_ci'])
        with self.assertRaisesRegex(GuardError, 'ERROR 1267'):
            self.db.sql('SELECT COUNT(*) FROM fee_reprice_collation_it '
                        'WHERE run_key=CONVERT(0x616263 USING utf8mb4)')

    def test_run_key_lookup_and_chinese_audit_json_round_trip(self):
        payload = json_exact({'name': '客户分佣：谢总', 'note': "引号'与反斜线\\及😀", 'fee': D('.0001')})
        self.db.sql('INSERT INTO fee_reprice_collation_it VALUES ('
                    + sql_value(RUN_KEY) + ',' + sql_value(payload) + ')')
        self.assertEqual(self.db.sql('SELECT COUNT(*) FROM fee_reprice_collation_it WHERE run_key='
                                    + sql_value(RUN_KEY)), ['1'])
        self.assertEqual(self.db.sql('SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(summary_json,\'$.name\'))) '
                                    'FROM fee_reprice_collation_it'),
                         ['客户分佣：谢总'.encode('utf-8').hex().upper()])
        self.assertEqual(self.db.sql('SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(summary_json,\'$.note\'))) '
                                    'FROM fee_reprice_collation_it'),
                         ["引号'与反斜线\\及😀".encode('utf-8').hex().upper()])
        self.assertEqual(self.db.sql('SELECT COLLATION(' + sql_value(RUN_KEY) + ')'),
                         ['utf8mb4_unicode_ci'])

    def test_audit_lookup_before_apply_after_rollback_and_after_commit(self):
        lookup = 'SELECT COUNT(*) FROM fee_reprice_collation_it WHERE run_key=' + sql_value(RUN_KEY)
        insert = 'INSERT INTO fee_reprice_collation_it VALUES (' + sql_value(RUN_KEY) + ',\'{"fee":0.1}\')'
        self.assertEqual(self.db.sql(lookup), ['0'])
        self.db.sql('START TRANSACTION')
        self.db.sql(insert)
        self.assertEqual(self.db.sql(lookup), ['1'])
        self.db.sql('ROLLBACK')
        self.assertEqual(self.db.sql(lookup), ['0'])
        self.db.sql('START TRANSACTION')
        self.db.sql(insert)
        self.db.sql('COMMIT')
        self.assertEqual(self.db.sql(lookup), ['1'])


if __name__ == '__main__':
    unittest.main()
