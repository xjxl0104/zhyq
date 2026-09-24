#!/usr/bin/env python3
"""Local-only marketing API acceptance. Creates synthetic fixtures in a disposable database.
Requires ADMIN_PASSWORD env; BASE_URL must resolve to a loopback hostname.
State contains test JWTs and stays mode 0600. Report contains only checks and synthetic IDs.
"""
import argparse, datetime as dt, io, json, os, secrets, sys, tempfile, urllib.request, urllib.error, urllib.parse, uuid, zipfile
from pathlib import Path
from xml.sax.saxutils import escape
BASE=os.environ.get('BASE_URL','http://127.0.0.1:19092/api').rstrip('/')
if urllib.parse.urlparse(BASE).hostname not in ('127.0.0.1','localhost','::1'):
    raise SystemExit('Refusing non-loopback API; use an isolated local test database.')
OUT=Path(os.environ.get('FLOW_OUTPUT',str(Path(tempfile.gettempdir())/'zhyq-marketing-acceptance')));OUT.mkdir(parents=True,exist_ok=True)
STATE=OUT/'flow-state.json';REPORT=OUT/'flow-report.json'
S=json.loads(STATE.read_text()) if STATE.exists() else {'run':dt.datetime.now().strftime('%m%d%H%M%S'),'checks':[],'password':secrets.token_hex(12)+'A1'}
def private_json(path,data):
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as out:json.dump(data,out,ensure_ascii=False,indent=2)
def save():
    private_json(STATE,S)
    private_json(OUT/'http-state.json',{'adminToken':S.get('admin'),'mpToken':S.get('mp1',{}).get('token'),'whToken':S.get('wh3',{}).get('token'),'ids':{k:S.get(k) for k in ['pid','wid','cid','bill','settlement','withdrawal']}})
    REPORT.write_text(json.dumps({'run':S['run'],'checks':S['checks'],'completed':S.get('completed',[])},ensure_ascii=False,indent=2))
def check(label,condition=True):
    assert condition,label
    if label not in S['checks']: S['checks'].append(label)
    save();print('PASS',label,flush=True)
def api(method,path,data=None,token=None,fail=None,raw=None,ctype=None):
    h={'Content-Type':ctype or 'application/json'}
    if token:h['Authorization']='Bearer '+token
    payload=raw if raw is not None else (json.dumps(data,ensure_ascii=False).encode() if data is not None else None)
    req=urllib.request.Request(BASE+path,data=payload,method=method,headers=h)
    try:
        with urllib.request.urlopen(req,timeout=30) as r: status=r.status;body=r.read()
    except urllib.error.HTTPError as e:status=e.code;body=e.read()
    try: result=json.loads(body)
    except Exception: result={'code':status,'message':body.decode(errors='replace')[:200]}
    okay=status<400 and result.get('code')==0
    if fail is not None:
        assert not okay,f'Expected rejection: {method} {path}'
        assert not fail or fail in result.get('message',''),f'Wrong rejection {path}: {result.get("message")}'
        return result
    if not okay:raise AssertionError(f'{method} {path} HTTP {status}, code={result.get("code")}, message={result.get("message")}')
    return result.get('data')
def upload(path,token,biz=None,bizid=None,filename='LOCAL-TEST-NOT-A-REAL-RECEIPT.pdf',content=None):
    boundary='----flow'+uuid.uuid4().hex;buf=bytearray()
    fields={} if biz is None else {'bizType':biz,'bizId':str(bizid)}
    for k,v in fields.items():buf.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    content=content or b'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 0/Kids []>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n'
    mime='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' if filename.endswith('.xlsx') else 'application/pdf'
    buf.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode());buf.extend(content);buf.extend(f'\r\n--{boundary}--\r\n'.encode())
    return api('POST',path,token=token,raw=bytes(buf),ctype='multipart/form-data; boundary='+boundary)
def rows(result):return result.get('records',[]) if isinstance(result,dict) else result

def front():
    a=S['admin']; run=S['run']
    for role,n in [('mp',1),('mp',2),('wh',3),('wh',4)]:
        k=role+str(n)
        if k not in S:
            phone='1'+f'{(int(run)+n)%10**10:010d}'
            body={'username':'flow_'+run+'_'+k,'password':S['password'],'phone':phone,'name':'本地测试'+k,'agreed':True}
            if role=='wh':body['warehouseName']='本地测试云仓'+run+k
            result=api('POST',f'/{role}/v1/auth/password-register',body)
            result['phone']=phone; S[k]=result;save()
        api('POST',f'/{role}/v1/auth/password-login',{'username':'flow_'+run+'_'+k,'password':S['password']})
    mp=S['mp1']['token'];othermp=S['mp2']['token']; wh=S['wh3']['token'];otherwh=S['wh4']['token']
    S['pid']=S['mp1']['me']['id'];S['wid']=S['wh3']['warehouseId'];S['otherwid']=S['wh4']['warehouseId'];save()
    check('password registration creates backend partner and warehouse records',api('GET',f'/crm/marketing/promoter/{S["pid"]}',token=a)['id']==S['pid'])
    api('GET','/wh/v1/apply',token=mp,fail='');api('GET','/mp/v1/me',token=wh,fail='')
    check('partner and merchant role tokens cannot cross access')
    for k in ['wh3','wh4']:
        token=S[k]['token']; wid=S[k]['warehouseId']; key='onboard_'+k
        if key not in S:
            profile=api('GET','/wh/v1/apply',token=token)
            profile.update({'region':'本地测试园区','address':'隔离环境测试地址','contact':'测试联系人','phone':S[k]['phone'],'areaSqm':1000,'dailyCapacity':10000,'categories':'本地测试','remark':'仅本地集成测试','settleCycle':3,'feeModel':'{"perOrder":5,"perItem":0}'} )
            if profile['joinStatus'] in (1,2): api('PUT','/wh/v1/apply',profile,token)
            api('PUT','/crm/marketing/warehouse',profile,a)
            if key+'_file' not in S:S[key+'_file']=upload('/wh/v1/files/upload',token);save()
            file=S[key+'_file']
            if profile['joinStatus'] in (1,2):
                api('POST','/wh/v1/apply/attachments',{'attachments':[{'id':file['id']}]},token)
                if profile['joinStatus']==1:api('POST','/wh/v1/apply/submit',token=token)
                api('POST',f'/crm/marketing/warehouse/{wid}/qualify/pass',{'version':api('GET',f'/crm/marketing/warehouse/{wid}',token=a)['version']},a)
            profile=api('GET','/wh/v1/apply',token=token)
            if profile['joinStatus']==3:api('POST',f'/crm/marketing/warehouse/{wid}/order-mode/manual',token=a)
            profile=api('GET','/wh/v1/apply',token=token)
            if profile['joinStatus']==4:api('POST',f'/crm/marketing/warehouse/{wid}/agreement',{'contractFile':'file:'+str(file['id'])},a)
            S[key]=True;save()
        online=api('GET','/wh/v1/apply',token=token)
        check(k+' manual onboarding works without claiming ERP connected',online['joinStatus']==5 and online['orderMode']=='manual' and online['erpStatus']==0)
    api('GET',f'/wh/v1/files/{S["onboard_wh3_file"]["id"]}',token=otherwh,fail='不属于')
    check('warehouse attachment ID cannot be read by another merchant')
    if 'cid' not in S:
        S['customer_phone']='1'+f'{(int(run)+8)%10**10:010d}';save()
        result=api('POST','/mp/v1/referral',{'name':'本地测试客户'+run,'contact':'测试客户','phone':S['customer_phone'],'serviceType':2,'warehouseId':S['wid'],'industry':'测试电商','demand':'本地测试需求','remark':'内部备注不得公开'},mp)
        S['cid']=result['customerId'];save()
    cid=S['cid']; cu=api('GET',f'/crm/marketing/customer/{cid}',token=a)
    check('referral persists intended warehouse',cu['intendedWarehouseId']==S['wid'])
    api('GET',f'/mp/v1/referral/{cid}',token=othermp,fail='无权')
    api('POST',f'/wh/v1/customers/{cid}/accept',token=otherwh,fail='无权')
    check('another partner or merchant cannot take the customer')
    if 'assigned' not in S:
        check('intention does not automatically assign merchant',cu['assignedWarehouseId'] is None)
        api('POST',f'/wh/v1/customers/{cid}/accept',token=wh,fail='无权')
        lock=api('GET',f'/crm/marketing/customer/{cid}/lock',token=a)
        if lock['status']==1:api('POST',f'/crm/marketing/customer/lock/{lock["id"]}/confirm',token=a)
        api('POST',f'/crm/marketing/customer/{cid}/grade',{'grade':'A','reason':'仅本地验收测试评级'},a)
        api('POST',f'/crm/marketing/customer/{cid}/assign-warehouse',{'warehouseId':S['wid'],'reason':'本地验收测试承接'},a)
        api('POST',f'/wh/v1/customers/{cid}/accept',token=wh)
        api('POST',f'/wh/v1/customers/{cid}/accept',token=wh)
        S['assigned']=True;save()
    api('POST',f'/wh/v1/customers/{cid}/progress',{'summary':'已沟通需求，等待签约'},wh)
    cu=api('GET',f'/mp/v1/referral/{cid}',token=mp)
    check('accepted warehouse and safe public progress visible to partner',cu['warehouseAssignmentStatus']==2 and cu['publicProgress']=='已沟通需求，等待签约' and '内部备注' not in json.dumps(cu,ensure_ascii=False))
    grades=api('GET','/crm/marketing/grade/list',token=a)
    if 'bonus_configured' not in S:
        grade=next(g for g in grades if g['code']=='A');grade['contractBonus']=2000
        api('PUT','/crm/marketing/grade',[grade],a);S['bonus_configured']=True;save()
    if 'contract' not in S:
        today=dt.date.today();S['period']={'periodStart':(today-dt.timedelta(days=8)).isoformat(),'periodEnd':today.isoformat()}
        body={'customerId':cid,'warehouseId':S['wid'],'signMode':1,'serviceType':2,'feeModel':2,'startDate':S['period']['periodStart'],'endDate':(today+dt.timedelta(days=60)).isoformat(),'deposit':0,'payCycle':3,'priceTable':'{"perOrder":10,"perItem":0}','remark':'本地测试合同，不构成实际法律文件'}
        S['contract']=api('POST','/crm/marketing/contract',body,a);save()
    co=S['contract']['id']; current=api('GET',f'/crm/marketing/contract/{co}',token=a)
    if current['status']==1:api('POST',f'/crm/marketing/contract/{co}/submit',token=a)
    current=api('GET',f'/crm/marketing/contract/{co}',token=a)
    if current['status']==2:api('POST',f'/crm/marketing/contract/{co}/audit',{'pass':True},a)
    current=api('GET',f'/crm/marketing/contract/{co}',token=a)
    if current['status']==3:
        api('POST',f'/crm/marketing/contract/{co}/sign-offline',{'files':'[]'},a,fail='附件')
        api('POST',f'/crm/marketing/contract/{co}/sign-offline',{'files':json.dumps([{'id':S['onboard_wh4_file']['id']}])},a,fail='不属于')
        api('POST',f'/crm/marketing/contract/{co}/sign-offline',{'files':json.dumps([{'id':S['onboard_wh3_file']['id']}])},a)
    api('POST',f'/crm/marketing/contract/{co}/sign-offline',{'files':json.dumps([{'id':S['onboard_wh3_file']['id']}])},a)
    cu=api('GET',f'/mp/v1/referral/{cid}',token=mp)
    check('contract activation syncs partner signed customer and deal lock',cu['status']==2 and cu['lockStatus']==3)
    api('POST',f'/crm/marketing/customer/{cid}/assign-warehouse',{'warehouseId':S['otherwid'],'reason':'故意测试禁止换仓'},a,fail='合同')
    api('POST',f'/crm/marketing/customer/{cid}/lose',{'reason':'故意测试禁止活跃合同流失'},a,fail='合同')
    check('active contract prevents warehouse swap and losing signed customer')
    whcustomers=rows(api('GET','/wh/v1/customers?pageSize=100',token=wh))
    check('merchant and partner use the same canonical customer assignment',any(c['id']==cid and c['status']==2 for c in whcustomers))
    if 'front' not in S.setdefault('completed',[]):S['completed'].append('front')
    save()

def xlsx(table):
    ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'
    sheet='<worksheet xmlns="'+ns+'"><sheetData>'
    for i,row in enumerate(table,1):
        sheet+='<row r="'+str(i)+'">'
        for j,value in enumerate(row):
            cell=chr(65+j)+str(i)
            sheet+='<c r="'+cell+'" t="inlineStr"><is><t>'+escape(str(value))+'</t></is></c>'
        sheet+='</row>'
    sheet+='</sheetData></worksheet>'
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
        z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        z.writestr('xl/workbook.xml','<workbook xmlns="'+ns+'" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="本地测试订单" sheetId="1" r:id="rId1"/></sheets></workbook>')
        z.writestr('xl/_rels/workbook.xml.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        z.writestr('xl/worksheets/sheet1.xml',sheet)
    return out.getvalue()

def funds():
    from decimal import Decimal
    a=S['admin'];mp=S['mp1']['token'];wh=S['wh3']['token'];otherwh=S['wh4']['token'];othermp=S['mp2']['token'];co=S['contract']['id'];run=S['run']
    if 'funds' in S.get('completed',[]):check('completed funds state preserved for UI review');return
    def own_commissions():return rows(api('GET',f'/crm/marketing/commission/page?promoterId={S["pid"]}&pageSize=100',token=a))
    head=['出库单号','客户手机号','件数','包裹数','发货时间','物流单号','云仓编码','货值']
    orderno='LOCAL-'+run
    table=[head,[orderno,S['customer_phone'],100,100,dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),'LOCAL-LOGISTICS','',1000]]
    if 'order' not in S:
        result=upload('/crm/marketing/order/import',a,filename='local-test.xlsx',content=xlsx(table))
        assert result['imported']==1 and not result['errors'], 'Import failed: '+str(result.get('errors'))
        S['order']=rows(api('GET','/crm/marketing/order/page?sourceNo='+orderno,token=a))[0]['id'];save()
    order=api('GET',f'/crm/marketing/order/{S["order"]}',token=a)
    check('manual outbound order preserves quantity and park-computed service fee',order['qty']==100 and order['packages']==100 and Decimal(str(order['serviceFee']))==1000)
    result=upload('/crm/marketing/order/import',a,filename='local-repeat.xlsx',content=xlsx(table))
    check('duplicate outbound import does not create duplicate order or commission',result['imported']==0 and result['skipped']==1)
    badtable=[head,['BAD-'+run,S['customer_phone'],-1,1,dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),'','',0]]
    bad=upload('/crm/marketing/order/import',a,filename='local-invalid.xlsx',content=xlsx(badtable))
    check('negative order quantity rejected without ledger mutation',bad['imported']==0 and len(bad['errors'])==1)
    outbound=rows(api('GET',f'/crm/marketing/order/{S["order"]}/splits',token=a))
    check('fresh outbound commissions retain real freeze period',len(outbound)>0 and all(c['status']==1 for c in outbound))
    if 'bill' not in S:
        S['bill']=api('POST','/crm/marketing/bill/generate',{'contractId':co,**S['period']},a)['id'];save()
    bill=S['bill']; same=api('POST','/crm/marketing/bill/generate',{'contractId':co,**S['period']},a)
    check('service bill generation is idempotent and exact',same['id']==bill and Decimal(str(same['amount']))==1000)
    api('GET',f'/wh/v1/bill/{bill}/lines',token=otherwh,fail='不属于')
    api('POST',f'/wh/v1/bill/{bill}/confirm',token=otherwh,fail='不属于')
    check('service bill ownership enforced for detail and confirmation')
    if 'bill_confirmed' not in S:
        api('POST',f'/wh/v1/bill/{bill}/dispute',{'reason':'本地演练：核对数量'},wh)
        api('POST',f'/crm/marketing/bill/{bill}/receive',{'receiptNo':'R-'+run,'receiptProof':'file:1','amount':1000},a,fail='')
        api('POST',f'/crm/marketing/bill/{bill}/resolve',{'reason':'本地演练：已核对原始出库单'},a)
        api('POST',f'/wh/v1/bill/{bill}/confirm',token=wh)
        api('POST',f'/wh/v1/bill/{bill}/confirm',token=wh)
        S['bill_confirmed']=True;save()
    check('bill dispute must be resolved then merchant confirmation can safely repeat')
    if 'bill_received' not in S:
        api('POST','/crm/marketing/settlement/generate',{'warehouseId':S['wid'],**S['period']},a,fail='已收款')
        api('POST',f'/crm/marketing/bill/{bill}/receive',{'receiptNo':'R-'+run,'receiptProof':'/uploads/not-real.pdf','amount':1000},a,fail='凭证')
        api('POST',f'/crm/marketing/bill/{bill}/receive',{'receiptNo':'R-'+run,'receiptProof':'file:1','amount':999},a,fail='金额')
        S['receipt_file']=upload('/file/upload',a,'mkt_bill',bill);save()
        body={'receiptNo':'R-'+run,'receiptProof':'file:'+str(S['receipt_file']['id']),'amount':1000}
        api('POST',f'/crm/marketing/bill/{bill}/receive',body,a);api('POST',f'/crm/marketing/bill/{bill}/receive',body,a)
        S['bill_received']=True;save()
    api('DELETE',f'/file/{S["receipt_file"]["id"]}',token=a,fail='')
    check('only confirmed exact receipts with stored proof settle bills; receipt proof is retained')
    bonuses=[c for c in own_commissions() if c['referralOrderId']!=S['order']]
    if 'commission_settled' not in S:
        check('real service receipt unfreezes contract signing bonus',bool(bonuses) and any(c['status']==2 for c in bonuses))
        ids=[c['id'] for c in bonuses if c['status']==2 and c['sign']==1]
        api('POST','/crm/marketing/commission/settle',{'ids':ids},a)
        S['commission_settled']=True;save()
    if 'settlement' not in S:
        settlement=api('POST','/crm/marketing/settlement/generate',{'warehouseId':S['wid'],**S['period']},a)
        check('warehouse payable uses cost rates rather than customer revenue',Decimal(str(settlement['amount']))==500 and settlement['status']==2)
        S['settlement']=settlement['id'];save()
    sid=S['settlement'];same=api('POST','/crm/marketing/settlement/generate',{'warehouseId':S['wid'],**S['period']},a)
    check('warehouse settlement generation is idempotent',same['id']==sid)
    api('GET',f'/wh/v1/settlement/{sid}/lines',token=otherwh,fail='不属于');api('POST',f'/wh/v1/settlement/{sid}/confirm',token=otherwh,fail='不属于')
    safe=api('GET',f'/wh/v1/settlement/{sid}/lines',token=wh)
    check('merchant settlement detail hides internal commission and sale price',all('commission' not in json.loads(x['snapshotJson']) and 'serviceFee' not in json.loads(x['snapshotJson']) for x in safe))
    if 'settlement_paid' not in S:
        api('POST',f'/crm/marketing/settlement/{sid}/pay',{'payNo':'WH-P-'+run,'payProof':'/uploads/not-real.pdf','amount':500},a,fail='')
        api('POST',f'/wh/v1/settlement/{sid}/confirm',token=wh);api('POST',f'/wh/v1/settlement/{sid}/confirm',token=wh)
        S['settlement_file']=upload('/file/upload',a,'mkt_settlement',sid);save()
        api('POST',f'/crm/marketing/settlement/{sid}/pay',{'payNo':'WH-P-'+run,'payProof':'file:'+str(S['receipt_file']['id']),'amount':500},a,fail='不属于')
        body={'payNo':'WH-P-'+run,'payProof':'file:'+str(S['settlement_file']['id']),'amount':500}
        api('POST',f'/crm/marketing/settlement/{sid}/pay',body,a);api('POST',f'/crm/marketing/settlement/{sid}/pay',body,a)
        S['settlement_paid']=True;save()
    check('confirmed merchant settlement paid once with its own stored proof',next(x for x in rows(api('GET','/wh/v1/settlement/page',token=wh)) if x['id']==sid)['status']==4)
    if 'account_reviewed' not in S:
        api('POST','/mp/v1/withdrawal',{'amount':100},mp,fail='')
        base='11010119900101001'; weights=[7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2]; checkdigit='10X98765432'[sum(int(v)*w for v,w in zip(base,weights))%11]
        S['account_payload']={'realName':'本地虚构测试用户','idNo':base+checkdigit,'accountType':'2','accountNo':'6222000000000000123','bankName':'本地测试银行'};save()
        api('PUT','/mp/v1/me/account',S['account_payload'],mp)
        api('POST','/mp/v1/withdrawal',{'amount':100},mp,fail='')
        account=api('GET',f'/crm/marketing/promoter/{S["pid"]}/account',token=a)
        api('POST',f'/crm/marketing/promoter/{S["pid"]}/account/review',{'version':account['version'],'pass':True,'reason':'仅隔离本地测试资料审核'},a)
        api('POST',f'/crm/marketing/promoter/{S["pid"]}/account/review',{'version':account['version'],'pass':True,'reason':'重复审核应拒绝'},a,fail='')
        S['account_reviewed']=True;save()
    safe=api('GET','/mp/v1/me/account',token=mp)
    check('payout account requires versioned human approval and is masked to partner',safe['reviewStatus']==1 and 'idNo' not in safe and 'accountNo' not in safe)
    if 'withdrawal' not in S:
        balance=api('GET','/mp/v1/withdrawal/balance',token=mp)
        check('settled signing commission becomes withdrawable',Decimal(str(balance['balance']))>=Decimal(str(balance['minWithdraw'])))
        S['withdrawal']=api('POST','/mp/v1/withdrawal',{'amount':str(balance['balance'])},mp)['id'];save()
    wd=S['withdrawal']
    if 'withdrawal_paid' not in S:
        api('POST','/mp/v1/withdrawal',{'amount':100},mp,fail='余额不足')
        api('PUT','/mp/v1/me/account',S['account_payload'],mp,fail='处理中提现')
        api('POST',f'/crm/marketing/withdrawal/{wd}/approve',token=mp,fail='')
        api('GET',f'/crm/marketing/withdrawal/{wd}/pay-account',token=othermp,fail='')
        own_records=rows(api('GET','/mp/v1/withdrawal/page',token=othermp))
        check('another partner cannot access withdrawal or its payout snapshot',all(x['id']!=wd for x in own_records))
        api('POST',f'/crm/marketing/withdrawal/{wd}/approve',token=a)
        snapshot=api('GET',f'/crm/marketing/withdrawal/{wd}/pay-account',token=a)
        check('finance reads immutable application account snapshot',snapshot['accountNo']==S['account_payload']['accountNo'])
        api('POST',f'/crm/marketing/withdrawal/{wd}/pay',{'payNo':'WD-P-'+run,'payProof':'file:'+str(S['settlement_file']['id'])},a,fail='不属于')
        S['withdrawal_file']=upload('/file/upload',a,'mkt_withdrawal',wd);save()
        body={'payNo':'WD-P-'+run,'payProof':'file:'+str(S['withdrawal_file']['id'])}
        paid=api('POST',f'/crm/marketing/withdrawal/{wd}/pay',body,a);same=api('POST',f'/crm/marketing/withdrawal/{wd}/pay',body,a)
        check('approved withdrawal is paid exactly once and response hides encrypted account',paid['id']==same['id'] and paid['status']==3 and 'accountNoEnc' not in paid)
        S['withdrawal_paid']=True;save()
    final=rows(api('GET','/mp/v1/withdrawal/page',token=mp));withdrawn=next(x for x in final if x['id']==wd)
    check('paid withdrawal visible to partner and linked commissions marked withdrawn',withdrawn['status']==3 and all(c['status']==4 for c in own_commissions() if c['withdrawalId']==wd))
    api('DELETE',f'/file/{S["withdrawal_file"]["id"]}',token=a,fail='')
    check('withdrawal payment proof cannot be deleted after payout')
    S.setdefault('completed',[]).append('funds');save()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--stage',choices=['front','funds','all'],default='all');args=parser.parse_args()
    S['admin']=api('POST','/auth/login',{'username':os.environ.get('ADMIN_USERNAME','admin'),'password':os.environ['ADMIN_PASSWORD']})['token'];save()
    if args.stage in ('front','all'):front()
    if args.stage in ('funds','all'):
        funds()
    print('REPORT',str(REPORT),flush=True)
