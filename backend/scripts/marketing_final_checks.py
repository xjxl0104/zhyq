#!/usr/bin/env python3
"""Additional checks against the last rebuilt local backend; never use a remote target."""
import marketing_flow as f
from decimal import Decimal
from concurrent.futures import ThreadPoolExecutor
import datetime as dt,json
S=f.S;a=S['admin'];mp=S['mp1']['token'];wh=S['wh3']['token'];otherwh=S['wh4']['token'];run=S['run'];co=S['direct_contract']

def direct_outbound():
    head=['出库单号','客户手机号','件数','包裹数','发货时间','物流单号','云仓编码','货值'];no='DIRECT-OUT-'+run
    content=f.xlsx([head,[no,S['direct_phone'],12,3,dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),'LOCAL-DIRECT-LOGISTICS','',200]])
    if 'direct_outbound' not in S:
        result=f.upload('/crm/marketing/order/import',a,filename='local-direct.xlsx',content=content)
        assert result['imported']==1 and not result['errors'],str(result.get('errors'))
        S['direct_outbound']=f.rows(f.api('GET','/crm/marketing/order/page?sourceNo='+no,token=a))[0]['id'];f.save()
    o=f.api('GET',f'/crm/marketing/order/{S["direct_outbound"]}',token=a)
    f.check('direct outbound order persists real quantities without park revenue or commission',o['qty']==12 and o['packages']==3 and Decimal(str(o['serviceFee']))==0 and Decimal(str(o['poolAmount']))==0 and o['promoterId'] is None)
    f.check('direct outbound creates no commission rows',not f.api('GET',f'/crm/marketing/order/{o["id"]}/splits',token=a))
    again=f.upload('/crm/marketing/order/import',a,filename='local-direct-repeat.xlsx',content=content)
    f.check('direct operational order retry does not duplicate',again['skipped']==1 and again['imported']==0)
    merchant=f.rows(f.api('GET','/wh/v1/orders/page?pageSize=100',token=wh));outsider=f.rows(f.api('GET','/wh/v1/orders/page?pageSize=100',token=otherwh))
    f.check('direct operational order is visible only to assigned merchant',any(x['id']==o['id'] for x in merchant) and all(x['id']!=o['id'] for x in outsider))

def direct_bonus():
    if 'direct_last_payment' not in S:
        receipt=f.upload('/file/upload',a,'mkt_direct_payment',co);S['direct_last_file']=receipt;f.save()
        body={'contractId':co,'warehouseId':S['wid'],'paymentNo':'DIRECT-P2-'+run,'amount':100,'payProof':'file:'+str(receipt['id'])}
        S['direct_last_payment']=f.api('POST','/crm/marketing/settlement/direct-payment',body,a)['id'];f.save()
    bonus=next(x for x in f.rows(f.api('GET','/crm/marketing/order/page?sourceNo=BONUS-'+str(co),token=a)) if x['sourceNo']=='BONUS-'+str(co))
    splits=f.api('GET',f'/crm/marketing/order/{bonus["id"]}/splits',token=a)
    f.check('actual direct platform receipt also unfreezes contract bonus and starts performance',all(x['status'] in (2,3,4) for x in splits) and f.api('GET',f'/crm/marketing/contract/{co}',token=a)['status']==5)

def final_payout():
    if S.get('final_paid'):return
    commissions=f.rows(f.api('GET',f'/crm/marketing/commission/page?promoterId={S["pid"]}&pageSize=100',token=a))
    eligible=[x['id'] for x in commissions if x['status']==2 and x['sign']==1]
    if eligible:f.api('POST','/crm/marketing/commission/settle',{'ids':eligible},a)
    order=f.rows(f.api('GET','/crm/marketing/order/page?sourceNo=DIRECT-P2-'+run,token=a))[0]
    if 'final_withdrawal' not in S:
        amount=f.api('GET','/mp/v1/withdrawal/balance',token=mp)['balance']
        def attempt(fn):
            try:return {'success':True,'data':fn()}
            except AssertionError:return {'success':False}
        with ThreadPoolExecutor(max_workers=2) as pool:
            apply=pool.submit(attempt,lambda:f.api('POST','/mp/v1/withdrawal',{'amount':str(amount)},mp))
            refund=pool.submit(attempt,lambda:f.api('POST',f'/crm/marketing/order/{order["id"]}/void',{'reason':'本地并发退款与提现互斥验收'},a))
            applied,voided=apply.result(),refund.result()
        f.check('concurrent refund and withdrawal cannot both commit against stale commissions',applied['success'] != voided['success'])
        if applied['success']:S['final_withdrawal']=applied['data']['id']
        else:
            balance=f.api('GET','/mp/v1/withdrawal/balance',token=mp)['balance']
            S['final_withdrawal']=f.api('POST','/mp/v1/withdrawal',{'amount':str(balance)},mp)['id']
            f.check('refund debit is deducted before the next full balance withdrawal',Decimal(str(balance))<Decimal(str(amount)))
        f.save()
    wd=S['final_withdrawal'];state=next(x for x in f.rows(f.api('GET','/mp/v1/withdrawal/page?pageSize=100',token=mp)) if x['id']==wd)
    if state['status']==1:f.api('POST',f'/crm/marketing/withdrawal/{wd}/approve',token=a)
    receipt=f.upload('/file/upload',a,'mkt_withdrawal',wd);body={'payNo':'FINAL-WD-'+run,'payProof':'file:'+str(receipt['id'])}
    f.api('POST',f'/crm/marketing/promoter/{S["pid"]}/freeze',{'reason':'本地已审核提现冻结打款验收'},a)
    try:
        f.api('POST',f'/crm/marketing/withdrawal/{wd}/pay',body,a,fail='冻结或退出')
        f.check('newly frozen partner cannot be paid even after withdrawal approval')
    finally:f.api('POST',f'/crm/marketing/promoter/{S["pid"]}/unfreeze',token=a)
    paid=f.api('POST',f'/crm/marketing/withdrawal/{wd}/pay',body,a);same=f.api('POST',f'/crm/marketing/withdrawal/{wd}/pay',body,a)
    f.check('unfrozen approved payout succeeds once with retained snapshot and proof',paid['id']==same['id'] and paid['status']==3)
    S['final_paid']=True;f.save()

f.api('POST',f'/crm/marketing/order/{S["order"]}/void',{'reason':'本地测试已入账订单不可直接作废'},a,fail='联系财务')
f.check('billed order cannot be silently cancelled and remains confirmed',f.api('GET',f'/crm/marketing/order/{S["order"]}',token=a)['status']==2)
direct_outbound();direct_bonus();final_payout()
print('FINAL CHECKS COMPLETE')
