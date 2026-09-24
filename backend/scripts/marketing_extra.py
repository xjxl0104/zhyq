#!/usr/bin/env python3
import marketing_flow as f
from decimal import Decimal
import datetime as dt,json,calendar
S=f.S;a=S['admin'];mp=S['mp1']['token'];wh=S['wh3']['token'];otherwh=S['wh4']['token']

def make_customer(key,n):
    if key+'_customer' in S:return S[key+'_customer']
    phone='1'+f'{(int(S["run"])+n)%10**10:010d}'
    cid=f.api('POST','/mp/v1/referral',{'name':'本地测试'+key+S['run'],'contact':'本地测试','phone':phone,'serviceType':2,'warehouseId':S['wid'],'demand':'隔离库扩展验收'},mp)['customerId']
    lock=f.api('GET',f'/crm/marketing/customer/{cid}/lock',token=a)
    f.api('POST',f'/crm/marketing/customer/lock/{lock["id"]}/confirm',token=a)
    f.api('POST',f'/crm/marketing/customer/{cid}/grade',{'grade':'A','reason':'本地扩展验收'},a)
    f.api('POST',f'/crm/marketing/customer/{cid}/assign-warehouse',{'warehouseId':S['wid'],'reason':'本地扩展验收'},a)
    f.api('POST',f'/wh/v1/customers/{cid}/accept',token=wh)
    S[key+'_customer']=cid;S[key+'_phone']=phone;f.save();return cid

def direct():
    cid=make_customer('direct',22)
    if 'direct_contract' not in S:
        body={'customerId':cid,'serviceType':2,'feeModel':2,'startDate':dt.date.today().isoformat(),'endDate':(dt.date.today()+dt.timedelta(days=60)).isoformat(),'deposit':0,'payCycle':3,'priceTable':'{"perOrder":10,"perItem":0}','files':json.dumps([{'id':S['onboard_wh3_file']['id']}]),'remark':'本地虚构直签测试合同'}
        S['direct_contract']=f.api('POST','/wh/v1/contracts',body,wh)['id'];f.save()
    co=S['direct_contract'];c=f.api('GET',f'/crm/marketing/contract/{co}',token=a)
    if c['status']==1:
        f.api('POST',f'/wh/v1/contracts/{co}/submit',token=otherwh,fail='无权')
        f.api('POST',f'/wh/v1/contracts/{co}/submit',token=wh)
    c=f.api('GET',f'/crm/marketing/contract/{co}',token=a)
    if c['status']==2:f.api('POST',f'/crm/marketing/contract/{co}/audit',{'pass':True},a)
    f.check('merchant can submit a real attachment-backed direct contract for park approval',f.api('GET',f'/crm/marketing/contract/{co}',token=a)['status'] in (4,5))
    f.api('POST','/crm/marketing/bill/generate',{'contractId':co,'periodStart':dt.date.today().isoformat(),'periodEnd':dt.date.today().isoformat()},a,fail='直签')
    if 'direct_payment' not in S:
        S['direct_file']=f.upload('/file/upload',a,'mkt_direct_payment',co);f.save()
        body={'contractId':co,'warehouseId':S['wid'],'paymentNo':'DIRECT-P-'+S['run'],'amount':100,'payProof':'file:'+str(S['direct_file']['id'])}
        p=f.api('POST','/crm/marketing/settlement/direct-payment',body,a);again=f.api('POST','/crm/marketing/settlement/direct-payment',body,a)
        f.check('actual direct platform receipt is idempotent',p['id']==again['id'])
        body['amount']=101;f.api('POST','/crm/marketing/settlement/direct-payment',body,a,fail='其他到账')
        S['direct_payment']=p['id'];f.save()
    orders=f.rows(f.api('GET','/crm/marketing/order/page?sourceNo=DIRECT-P-'+S['run'],token=a));o=orders[0]
    f.check('platform commissions use actual receipt only',o['sourceType']==3 and Decimal(str(o['baseAmount']))==100 and Decimal(str(o['poolAmount']))>0)
    commissions=f.api('GET',f'/crm/marketing/order/{o["id"]}/splits',token=a)
    f.check('platform commission is immediately settleable after recorded receipt',len(commissions)>0 and all(x['status']==2 for x in commissions))
    f.api('DELETE',f'/file/{S["direct_file"]["id"]}',token=a,fail='')
    f.check('direct payment proof retained and direct contract cannot enter park service billing')

def month_add(day,months):
    offset=day.year*12+day.month-1+months;year,month=divmod(offset,12);month+=1
    return dt.date(year,month,min(day.day,calendar.monthrange(year,month)[1]))
def fixed():
    cid=make_customer('fixed',23)
    base=month_add(dt.date.today().replace(day=1),-2)
    start=base.replace(day=calendar.monthrange(base.year,base.month)[1])
    end=month_add(start,4).replace(day=15)
    if 'fixed_contract' not in S:
        body={'customerId':cid,'warehouseId':S['wid'],'signMode':1,'serviceType':1,'feeModel':4,'startDate':start.isoformat(),'endDate':end.isoformat(),'deposit':0,'payCycle':3,'priceTable':'{"monthly":310}','remark':'本地虚构固定月费测试合同'}
        S['fixed_contract']=f.api('POST','/crm/marketing/contract',body,a)['id'];f.save()
    co=S['fixed_contract'];c=f.api('GET',f'/crm/marketing/contract/{co}',token=a)
    if c['status']==1:f.api('POST',f'/crm/marketing/contract/{co}/submit',token=a)
    c=f.api('GET',f'/crm/marketing/contract/{co}',token=a)
    if c['status']==2:f.api('POST',f'/crm/marketing/contract/{co}/audit',{'pass':True},a)
    c=f.api('GET',f'/crm/marketing/contract/{co}',token=a)
    if c['status']==3:f.api('POST',f'/crm/marketing/contract/{co}/sign-offline',{'files':json.dumps([{'id':S['onboard_wh3_file']['id']}])},a)
    f.api('POST','/crm/marketing/bill/generate-fixed',token=a)
    f.check('repeated fixed-fee generation adds no duplicate bills',f.api('POST','/crm/marketing/bill/generate-fixed',token=a)==0)
    bills=f.rows(f.api('GET',f'/finance/bill/page?contractId={co}&source=mkt_service&pageSize=100',token=a))
    expected={month_add(start,i).isoformat() for i in range(5) if month_add(start,i)<=dt.date.today() and month_add(start,i)<=end}
    f.check('fixed monthly fees anchor to contract day and avoid future cycles',len(bills)==len(expected) and {b['periodStart'] for b in bills}==expected and all(Decimal(str(b['amount']))==310 for b in bills))
    first=next(x for x in bills if x['periodStart']==start.isoformat());S['fixed_first_bill']=first['id'];f.save()
    if 'fixed_payment' not in S:
        body={'billId':first['id'],'amount':310,'payMethod':'转账','payNo':'FIXED-P-'+S['run']}
        p=f.api('POST','/finance/payment',body,a);same=f.api('POST','/finance/payment',body,a)
        f.check('first anchored fixed rent receipt is idempotent',p['id']==same['id'])
        S['fixed_payment']=p['id'];f.save()
    bonus=next(x for x in f.rows(f.api('GET','/crm/marketing/order/page?sourceNo=BONUS-'+str(co),token=a)) if x['sourceNo']=='BONUS-'+str(co))
    commissions=f.api('GET',f'/crm/marketing/order/{bonus["id"]}/splits',token=a)
    f.check('first fixed rent receipt starts performance and unfreezes bonus',f.api('GET',f'/crm/marketing/contract/{co}',token=a)['status']==5 and all(x['status']==2 for x in commissions))
    if 'extras' not in S.setdefault('completed',[]):S['completed'].append('extras')
    f.save()

if __name__=='__main__':
    direct();fixed()
print('EXTRA CHECKS COMPLETE')
