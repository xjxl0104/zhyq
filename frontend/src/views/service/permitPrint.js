const esc = (v) => String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const box = (checked) => (checked ? '<span class="chk on">☑</span>' : '<span class="chk">□</span>')
const line = (v, minCh = 8) => `<span class="uline" style="min-width:${minCh}ch">${esc(v || '')}</span>`

export function parseDateParts(dateStr) {
  if (!dateStr) return { y: '', m: '', d: '' }
  const m = String(dateStr).match(/^(\d{4})-(\d{1,2})-(\d{1,2})/)
  if (!m) return { y: '', m: '', d: '' }
  return { y: m[1], m: String(Number(m[2])), d: String(Number(m[3])) }
}

export function parseDateTimeParts(dtStr) {
  if (!dtStr) return { m: '', d: '', h: '', min: '' }
  const match = String(dtStr).match(/^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{1,2})/)
  if (!match) return { m: '', d: '', h: '', min: '' }
  return { m: String(Number(match[2])), d: String(Number(match[3])), h: match[4].padStart(2, '0'), min: match[5].padStart(2, '0') }
}

export function extractHotWorkNoParts(applyNo) {
  const s = String(applyNo || '')
  const m = s.match(/[〔\[]\s*(\d{4})\s*[〕\]]\s*动字第\s*([A-Za-z0-9-]+)\s*号/)
  if (m) return { year: m[1], seq: m[2] }
  return { year: '', seq: s }
}

export function extractDecoSeq(applyNo) {
  const s = String(applyNo || '')
  const m = s.match(/DIPARK-装批-?(.+?)号?$/)
  return m ? m[1] : s
}

export function defaultHotWorkData() {
  return {
    fireLevel: '',
    fireMethods: [],
    fireMethodOther: '',
    floors: [],
    zones: [],
    startDateTime: '',
    endDateTime: '',
    fireLocation: '',
    confinedSpace: '',
    risks: [],
    riskOther: '',
    crossWorks: [],
    workLeader: '',
    workLeaderPhone: '',
    operatorName: '',
    certNo: '',
    watcherName: '',
    watcherPhone: '',
    verify1: '',
    verify2: '',
    verify3: '',
    securityVerifySign: '',
    gasTime: '',
    gasPart: '',
    gasType: '',
    gasConcentration: '',
    gasConclusion: '',
    safetyChecks: [],
    safetyWatcherSign: '',
    safetySecuritySign: '',
    safetyDate: '',
    approve1Choice: '',
    approve1Sign: '',
    approve2Choice: '',
    approve2Sign: '',
    approve3Choice: '',
    approve3Sign: '',
    approve4Choice: '',
    approve4Sign: '',
    finishChecks: [],
    finishTime: '',
    finishConclusion: '',
    finishWatcher: '',
    finishLeader: '',
    finishSecurity: '',
    finishProperty: '',
    disclosureLeader: '',
    disclosureOperator: '',
    disclosureDate: '',
    monitorRows: [
      { time: '', part: '', status: '', gas: '', sign: '' },
      { time: '', part: '', status: '', gas: '', sign: '' },
      { time: '', part: '', status: '', gas: '', sign: '' },
      { time: '', part: '', status: '', gas: '', sign: '' }
    ]
  }
}

export function defaultDecorationData() {
  return {
    creditCode: '',
    actualUser: '',
    actualUserPhone: '',
    zones: [],
    floors: [],
    area: '',
    wholeFloor: '',
    areaRatio: '',
    decoType: '',
    contents: [],
    contentOther: '',
    structureChange: '否',
    structureItems: [],
    structureOther: '',
    fireChange: '否',
    fireItems: [],
    planDays: '',
    qualNo: '',
    projectLeader: '',
    safetyLeader: '',
    specialWorkerCount: '',
    designCompany: '',
    designQualLevel: '',
    applicantStampText: '',
    legalRepSign: '',
    declareDate: '',
    receiver: '',
    receiveDate: '',
    receiveResult: '',
    missingDocs: '',
    attachChecks: [],
    neighbors: [],
    notifyWays: [],
    notifyOther: '',
    engOpinion: '',
    engSupplement: '',
    engSign: '',
    engDate: '',
    ehsOpinion: '',
    ehsSupplement: '',
    ehsSign: '',
    ehsDate: '',
    secOpinion: '',
    secSupplement: '',
    secSign: '',
    secDate: '',
    parkOpinion: '',
    parkReason: '',
    approvedStart: '',
    approvedEnd: '',
    parkSign: '',
    parkDate: '',
    preChecks: [],
    depositAmount: '',
    depositReceiptNo: '',
    insuranceNo: '',
    preVerifier: '',
    preVerifyDate: '',
    preVerifyResult: '',
    preRectifyItems: '',
    inspectApplyDate: '',
    actualFinishDate: '',
    inspectConclusion: '',
    inspectRectify: '',
    inspectEng: '',
    inspectEhs: '',
    inspectProp: '',
    inspectApplicant: '',
    inspectDate: '',
    depositRefundType: '',
    refundFullAmount: '',
    deductAmount: '',
    deductReason: '',
    actualRefundAmount: ''
  }
}

function sealImgHtml(seal) {
  if (!seal || !seal.enabled || !seal.imageData) return ''
  const widthPct = (((Number(seal.defaultWidthMm || 44) / 190) * 100) * Number(seal.scale || 1)).toFixed(2)
  const x = Number(seal.posX ?? 66).toFixed(2)
  const y = Number(seal.posY ?? 88).toFixed(2)
  const rot = Number(seal.rotation || 0).toFixed(1)
  const opacity = Number(seal.opacity ?? 0.92).toFixed(2)
  return `<img class="permit-seal" src="${seal.imageData}" alt="电子印章" style="left:${x}%;top:${y}%;width:${widthPct}%;opacity:${opacity};transform:translate(-50%,-50%) rotate(${rot}deg);" />`
}

const HOT_WORK_MEASURES = [
  '1. 动火点周围 15 米半径内易燃物清除；下方下水井、地漏、地沟已覆盖/水封隔离。',
  '2. 每个动火点配备不少于 2 具 5KG ABC 干粉灭火器，由施工方自备且在有效期内。',
  '3. 动火危险区域已设置警戒线及警示标识，无关人员禁止入内。',
  '4. 乙炔瓶直立，氧气瓶与乙炔瓶间距≥5 米、与火源间距≥10 米；回火防止器完好，软管无破损漏气。',
  '5. 电焊回路线直接接在焊件上，电焊机接地可靠，线缆绝缘无破损，未穿过下水井搭接。',
  '6.【高处/屋面专项】已采取防火花飞溅措施，风力≤5 级，人员已系挂安全带。',
  '7.【交叉作业专项】动火区与相邻 A/B/C 区储存区之间已用不燃材料做防火分隔。',
  '8. 现场专职监火人在岗、已接受专项安全培训、不兼做其他工作，全程不得离岗。',
  '9. 动火作业公示已张贴；配备防爆照明（如适用）；特级动火已落实全过程视频录像。',
  '10.【共用/相邻区域】动火已提前告知同栋相邻租户/使用人（多使用人共用时必填）。'
]

const HOT_WORK_FINISH = [
  '1. 明火熄灭，焊机气瓶已关闭',
  '2. 焊渣熔渣、余料清理干净',
  '3. 动火部位已测温，无阴燃',
  '4. 留守≥30 分钟，无复燃',
  '5. 移走可燃物已复位、隔离拆除',
  '6. 2 具 5KG 干粉灭火器已归位',
  '7. 区域清扫完毕，无遗留火种',
  '8. 动火后 4 小时内复查≥2 次'
]

export function buildHotWorkPages(record, rawData, seal = null) {
  const d = { ...defaultHotWorkData(), ...(rawData || {}) }
  const no = extractHotWorkNoParts(record?.applyNo)
  const ad = parseDateParts(record?.applyDate)
  const st = parseDateTimeParts(d.startDateTime)
  const et = parseDateTimeParts(d.endDateTime)
  const safetyD = parseDateParts(d.safetyDate)
  const discD = parseDateParts(d.disclosureDate)
  const gasParts = (d.gasTime || '').split(':')
  const finishParts = (d.finishTime || '').split(':')
  const has = (arr, v) => Array.isArray(arr) && arr.includes(v)
  const sealPage1 = seal && Number(seal.page || 1) === 1 ? sealImgHtml(seal) : ''
  const sealPage2 = seal && Number(seal.page || 1) === 2 ? sealImgHtml(seal) : ''

  const floors = ['1', '2', '3', '4', '5', '6', '7', '屋面'].map((f) => `${box(has(d.floors, f))}${f}`).join(' ')
  const zones = ['A区', 'B区', 'C区', '公共/通道'].map((z) => `${box(has(d.zones, z))}${z}`).join(' ')
  const methods = ['电焊', '气割', '角磨机', '其他'].map((m) => `${box(has(d.fireMethods, m))}${m}`).join(' ') + (d.fireMethodOther ? `(${esc(d.fireMethodOther)})` : '')
  const risks = ['易燃物积聚', '气瓶泄漏', '高处飞火', '其他'].map((r) => `${box(has(d.risks, r))}${r}`).join(' ') + (d.riskOther ? `(${esc(d.riskOther)})` : '')
  const cross = ['高处', '受限空间', '临时用电', '动土'].map((c) => `${box(has(d.crossWorks, c))}${c}`).join(' ')

  const safetyRows = HOT_WORK_MEASURES.map((text, idx) => `
    <tr>
      <td class="chk-col">${box(has(d.safetyChecks, idx + 1))}</td>
      <td>${text}</td>
    </tr>`).join('')

  const finishPairs = [0, 2, 4, 6].map((i) => `
    <tr>
      <td>${box(has(d.finishChecks, i + 1))} ${HOT_WORK_FINISH[i]}</td>
      <td>${box(has(d.finishChecks, i + 2))} ${HOT_WORK_FINISH[i + 1]}</td>
    </tr>`).join('')

  const monitorRows = (d.monitorRows && d.monitorRows.length ? d.monitorRows : defaultHotWorkData().monitorRows).slice(0, 4).map((r) => {
    const tp = (r.time || '').split(':')
    return `<tr>
      <td class="c">${line(tp[0] || '', 3)}时${line(tp[1] || '', 3)}分</td>
      <td>${esc(r.part || '')}</td>
      <td class="c">${box(r.status === '正常')}正常 &nbsp;${box(r.status === '异常')}异常</td>
      <td class="c">${esc(r.gas || '')}</td>
      <td class="c">${esc(r.sign || '')}</td>
    </tr>`
  }).join('')

  const page1 = `<div class="permit-page">
    <div class="doc-header">
      <div class="doc-title">DIPARK 数智云仓产业园动火作业审批表</div>
      <div class="doc-sub">正面 · 审批联（一式三联，联次与归档见背面第九条）</div>
    </div>
    <div class="doc-no">动火编号：〔 ${line(no.year, 4)} 〕动字第 ${line(no.seq, 5)} 号</div>

    <div class="sec-bar">一 &nbsp;作业基本信息</div>
    <table class="grid-table hw-info">
      <tr>
        <th>申请单位/租户</th><td>${esc(record?.applicantUnit || '')}</td>
        <th>申请日期</th><td class="c">${line(ad.y, 4)}年 ${line(ad.m, 2)}月 ${line(ad.d, 2)}日</td>
      </tr>
      <tr>
        <th>动火级别</th><td>${box(d.fireLevel === '特级')}特级 &nbsp;${box(d.fireLevel === '一级')}一级 &nbsp;${box(d.fireLevel === '二级')}二级</td>
        <th>动火方式</th><td>${methods}</td>
      </tr>
      <tr>
        <th>作业区域<br>（可多选）</th>
        <td>楼层：${floors}<br>分区：${zones}</td>
        <th>动火时间</th>
        <td>${line(st.m, 2)}月${line(st.d, 2)}日${line(st.h, 2)}时${line(st.min, 2)}分 至 ${line(et.m, 2)}月${line(et.d, 2)}日${line(et.h, 2)}时${line(et.min, 2)}分</td>
      </tr>
      <tr>
        <th>动火点位</th><td>${esc(d.fireLocation || record?.workArea || '')}</td>
        <th>密闭空间</th><td>${box(d.confinedSpace === '有')}有 &nbsp;${box(d.confinedSpace === '无')}无</td>
      </tr>
      <tr>
        <th>主要风险<br>辨识</th><td>${risks}</td>
        <th>交叉作业</th><td>${cross}（如有须另附对应审批单）</td>
      </tr>
      <tr>
        <th>作业负责人</th><td>${esc(d.workLeader || record?.contact || '')}</td>
        <th>联系电话</th><td>${esc(d.workLeaderPhone || record?.phone || '')}</td>
      </tr>
      <tr>
        <th>动火作业人</th><td>${esc(d.operatorName || '')}</td>
        <th>特种作业证号</th><td>${esc(d.certNo || '')}</td>
      </tr>
      <tr>
        <th>现场监火人</th><td>${esc(d.watcherName || '')}</td>
        <th>联系电话</th><td>${esc(d.watcherPhone || '')}</td>
      </tr>
    </table>
    <div class="tiny-note">注：特级/一级有效≤8h，二级≤72h；夜间/节假日动火升级管理；超时作废。</div>

    <div class="sec-bar">二 &nbsp;动火人资质及现场核验（安保填写）</div>
    <div class="plain-block">
      <div>1. 特种作业操作证在有效期内、类别一致：${box(d.verify1 === '是')}是 &nbsp;${box(d.verify1 === '否')}否</div>
      <div>2. 人证一致、本人到场：${box(d.verify2 === '是')}是 &nbsp;${box(d.verify2 === '否')}否</div>
      <div>3. 审批单在有效期内、风险相符：${box(d.verify3 === '是')}是 &nbsp;${box(d.verify3 === '否')}否</div>
      <div>安保核验签字：${line(d.securityVerifySign, 14)}</div>
    </div>

    <div class="sec-bar">三 &nbsp;可燃气体检测（强制前置条件）</div>
    <div class="gas-meta">检测时间：${line(gasParts[0] || '', 4)}时${line(gasParts[1] || '', 4)}分 &nbsp;部位：${line(d.gasPart, 16)} &nbsp;检测气体：${line(d.gasType, 14)}</div>
    <table class="grid-table">
      <thead><tr><th style="width:24%">浓度（%LEL）</th><th style="width:42%">合格判定阈值</th><th style="width:34%">结论</th></tr></thead>
      <tbody>
        <tr>
          <td class="c">${esc(d.gasConcentration || '')}</td>
          <td>爆炸下限≥4%：浓度≤0.5%；＜4%：≤0.2%（体积）即合格</td>
          <td>${box(d.gasConclusion === '合格')}合格，准许动火 &nbsp;${box(d.gasConclusion === '不合格')}不合格，禁止动火</td>
        </tr>
      </tbody>
    </table>
    <div class="tiny-note">*注：特级/一级中断＞30min、二级中断＞60min 或每日复工前须重新检测合格；取样与动火间隔≤30min。</div>

    <div class="sec-bar">四 &nbsp;安全措施逐项确认（监火人/安保共同核对打勾，缺一不可）</div>
    <table class="grid-table safety-table">
      <tbody>${safetyRows}</tbody>
    </table>
    <div class="sign-line">现场监火人签字：${line(d.safetyWatcherSign, 11)} &nbsp;&nbsp;安保核验人签字：${line(d.safetySecuritySign, 11)} &nbsp;&nbsp;日期：${line(safetyD.m, 3)}月${line(safetyD.d, 3)}日</div>

    <div class="sec-bar">五 &nbsp;审批意见（由下至上逐级签署，缺一级不生效）</div>
    <div class="plain-block approve-list">
      <div class="bold-tip">【权限提示】特级：园区主要负责人终审；一级：物业安全负责人/经理审批；二级：属地主管审批。</div>
      <div>1. 施工方／申请单位负责人 &nbsp;${box(d.approve1Choice === '同意')}已落实全部措施，同意动火 &nbsp;${box(d.approve1Choice === '不同意')}措施未落实，暂不动火 &nbsp;签字/日期：${line(d.approve1Sign, 12)}</div>
      <div>2. 物业安全主管（安保队长） &nbsp;${box(d.approve2Choice === '同意')}现场条件符合，同意动火 &nbsp;${box(d.approve2Choice === '不同意')}现场条件不符，不同意 &nbsp;签字/日期：${line(d.approve2Sign, 12)}</div>
      <div>3. 物业工程／消防主管 &nbsp;${box(d.approve3Choice === '同意')}消防设施器材到位，同意 &nbsp;${box(d.approve3Choice === '不同意')}不到位，不同意 &nbsp;签字/日期：${line(d.approve3Sign, 12)}</div>
      <div>4. 物业项目经理（终审） &nbsp;${box(d.approve4Choice === '批准')}批准动火 &nbsp;${box(d.approve4Choice === '不批准')}不批准动火 &nbsp;签字/审批章：${line(d.approve4Sign, 14)}</div>
    </div>

    <div class="doc-footer">DIPARK 数智云仓产业园 物业服务中心制 &nbsp;&nbsp;第 1 页／共 2 页（正面·审批联）</div>
    ${sealPage1}
  </div>`

  const page2 = `<div class="permit-page">
    <div class="doc-header">
      <div class="doc-title">DIPARK 数智云仓产业园动火作业审批表</div>
      <div class="doc-sub">反面 · 验收联</div>
    </div>
    <div class="doc-no">动火编号：〔 ${line(no.year, 4)} 〕动字第 ${line(no.seq, 5)} 号 &nbsp;（与正面同一编号，缺一不可）</div>

    <div class="sec-bar">六 &nbsp;完工验收与现场恢复确认（动火结束后 30 分钟内填写）</div>
    <table class="grid-table half-table"><tbody>${finishPairs}</tbody></table>
    <div class="plain-block">
      <div>完工时间：${line(finishParts[0] || '', 3)}时${line(finishParts[1] || '', 3)}分 &nbsp;&nbsp;验收结论：${box(d.finishConclusion === '合格')}合格，现场已恢复 &nbsp;${box(d.finishConclusion === '不合格')}不合格，须整改</div>
      <div>监火人：${line(d.finishWatcher, 9)} &nbsp;施工负责人：${line(d.finishLeader, 9)} &nbsp;安保验收：${line(d.finishSecurity, 9)} &nbsp;物业复核：${line(d.finishProperty, 9)}</div>
    </div>

    <div class="sec-bar">七 &nbsp;动火作业安全告知与交底（施工方逐条阅读后签字确认）</div>
    <div class="plain-block clause-list">
      <div>1. 三不动火：无审批票不动火；无可燃气体检测合格凭证不动火；监火人不在场不动火。安保有权随时叫停。</div>
      <div>2. 票证时效：一事一票，当日当次有效。动火时间、地点、内容任一变更，原票作废，须重新申请。</div>
      <div>3. 灭火器材：每个动火点须配 2 具 5KG ABC 干粉灭火器，施工方自备、进场交安保查验，全程置于动火点旁。</div>
      <div>4. 禁止行为：动火期间禁止同区域刷漆、喷胶、清洗、除尘等产生可燃气体的作业；禁止在可燃物上方直接动火；禁止擅自扩大动火范围。</div>
      <div>5. 应急处置：发现火情立即停止作业、切断电源气源，就近用干粉灭火器扑救初起火灾，同时报园区值班室并拨打 119；火势失控立即疏散，不得盲目扑救。</div>
      <div>6. 收尾责任：作业结束须断电、关气瓶阀、清理现场，经安保验收签字后方可离场；未验收离场视为未完工，后果由施工方承担。</div>
      <div class="sign-line">施工方已阅读并接受上述全部告知：负责人签字${line(d.disclosureLeader, 10)} &nbsp;作业人签字${line(d.disclosureOperator, 10)} &nbsp;${line(discD.m, 3)}月${line(discD.d, 3)}日</div>
    </div>

    <div class="sec-bar">八 &nbsp;现场监护记录（监火人每 30 分钟记录一次，异常立即停止作业）</div>
    <table class="grid-table monitor-table">
      <thead>
        <tr>
          <th style="width:18%">记录时间</th>
          <th style="width:24%">动火部位</th>
          <th style="width:20%">现场情况</th>
          <th style="width:18%">气体复测<br>%LEL</th>
          <th style="width:20%">监火人签字</th>
        </tr>
      </thead>
      <tbody>${monitorRows}</tbody>
    </table>

    <div class="sec-bar center">九 &nbsp;票证联次与归档要求</div>
    <div class="plain-block clause-list">
      <div>1. 一式三联：第一联申请单位（施工方）留存；第二联物业服务中心存档；第三联承包方安保随身备查。（单张双面打印时，第三联以复印件留存归档）</div>
      <div>2. 随票附件：可燃气体检测记录、特种作业操作证复印件、动火公示照片、现场安全措施照片（动火前后各一组）。</div>
      <div>3. 存档期限：本表及附件由物业服务中心存档不少于 1 年，供消防检查与事故倒查；发生火情一律永久留存。</div>
      <div>4. 违规处理：无票动火、超时超范围动火、监火人脱岗的，安保立即制止并上报项目经理；造成后果的按租赁合同及安全协议追责，必要时移交消防主管部门。</div>
    </div>

    <div class="doc-footer">DIPARK 数智云仓产业园 物业服务中心制 &nbsp;&nbsp;第 2 页／共 2 页（反面·验收联）</div>
    ${sealPage2}
  </div>`

  return [page1, page2]
}

const DECO_PRE_CHECKS = [
  '装修材料燃烧性能达标（顶棚 A 级、墙面/隔断≥B1 级、有 GB 8624 检测报告）',
  '特种作业人员证照核验（电工/焊工/高处/吊装）且在有效期内',
  '施工人员出入证已办理，三级安全教育已完成',
  '临时用电方案已报审，配电箱/漏保/线缆合格',
  '动火作业票已另行申请（如需），灭火器≥2 具/5kg 干粉已就位',
  '高处作业防坠落措施到位（≥2m 必须挂安全带/设护栏）',
  '施工区域与生产/运营区域有效隔离（硬质围挡+警示标识）',
  '楼层消防通道、疏散出口、消火栓未被占用或遮挡',
  '噪声/粉尘/废弃物管理措施已落实，不影响同层及相邻区域使用方'
]

export function buildDecorationPages(record, rawData, seal = null) {
  const d = { ...defaultDecorationData(), ...(rawData || {}) }
  const seq = extractDecoSeq(record?.applyNo)
  const ad = parseDateParts(record?.applyDate)
  const sd = parseDateParts(record?.startDate)
  const ed = parseDateParts(record?.endDate)
  const rd = parseDateParts(d.receiveDate)
  const engD = parseDateParts(d.engDate)
  const ehsD = parseDateParts(d.ehsDate)
  const secD = parseDateParts(d.secDate)
  const parkS = parseDateParts(d.approvedStart)
  const parkE = parseDateParts(d.approvedEnd)
  const parkD = parseDateParts(d.parkDate)
  const preD = parseDateParts(d.preVerifyDate)
  const insAppD = parseDateParts(d.inspectApplyDate)
  const actFinD = parseDateParts(d.actualFinishDate)
  const insD = parseDateParts(d.inspectDate)
  const has = (arr, v) => Array.isArray(arr) && arr.includes(v)
  const sealPage1 = seal && Number(seal.page || 2) === 1 ? sealImgHtml(seal) : ''
  const sealPage2 = seal && Number(seal.page || 2) === 2 ? sealImgHtml(seal) : ''

  const zones = ['A区', 'B区', 'C区'].map((z) => `${box(has(d.zones, z))}${z}`).join(' &nbsp;／&nbsp; ')
  const floors = ['1F', '2F', '3F', '4F', '5F', '6F', '7F', '夹层', '屋面/天台'].map((f) => `${box(has(d.floors, f))}${f}`).join(' &nbsp;')
  const decoTypes = ['首次装修', '翻新改造', '扩建加层', '局部修缮', '设备安装调试'].map((t) => `${box(d.decoType === t)}${t}`).join(' &nbsp;')
  const contents1 = ['地面', '墙面', '天花吊顶', '隔断', '水电改造', '暖通消防', '外立面', '门窗更换'].map((c) => `${box(has(d.contents, c))}${c}`).join(' &nbsp;')
  const contents2 = ['电梯', '货架/重型设备', '其他'].map((c) => `${box(has(d.contents, c))}${c}`).join(' &nbsp;')
  const structItems = ['承重墙开洞', '梁/柱变动', '楼板开孔', '屋面荷载增加', '其他'].map((s) => `${box(has(d.structureItems, s))}${s}`).join(' ')
  const fireItems = ['喷淋', '烟感/报警', '防火卷帘', '疏散通道', '消火栓', '防排烟'].map((f) => `${box(has(d.fireItems, f))}${f}`).join(' ')
  const attachList = ['装修方案', '效果图/平面图', '施工/设计资质', '人员证照', '材料清单（燃烧性能）', '消防报审', '保险单'].map((a) => `${box(has(d.attachChecks, a))}${a}`).join(' ')

  const neighborList = ['同区同层相邻租户/使用人', '同区上一层', '同区下一层', '相邻区（A／B／C 区之间）使用人', '涉及消防通道通行方', '物业管理处'].map((n) => `${box(has(d.neighbors, n))}${n}`).join(' ')
  const preCheckLines = DECO_PRE_CHECKS.map((t, i) => `<div>${box(has(d.preChecks, i + 1))} ${t}</div>`).join('')

  const page1 = `<div class="permit-page deco-page">
    <div class="doc-header">
      <div class="doc-title">DIPARK 数智云仓产业园装修报批申请表</div>
      <div class="doc-sub">正面 · 申请联</div>
    </div>
    <div class="doc-no flex-between">
      <span>编号：DIPARK-装批-${line(seq, 8)}号</span>
      <span>申请日期：${line(ad.y, 4)}年 ${line(ad.m, 2)}月 ${line(ad.d, 2)}日</span>
    </div>

    <div class="sec-bar">一、申请单位信息</div>
    <div class="deco-block">
      <div><b>承租单位（全称）：</b>${line(record?.applicantUnit, 34)}</div>
      <div><b>统一社会信用代码：</b>${line(d.creditCode, 20)} &nbsp;<b>联系人：</b>${line(record?.contact, 8)} &nbsp;<b>联系电话：</b>${line(record?.phone, 12)}</div>
      <div><b>实际使用人（若与承租方不同）：</b>${line(d.actualUser, 18)} &nbsp;<b>联系电话：</b>${line(d.actualUserPhone, 13)}</div>
    </div>

    <div class="sec-bar">二、装修部位（按分区·楼层定位）</div>
    <div class="deco-block">
      <div><b>所 在 区：</b>${zones}</div>
      <div><b>楼 层：</b>${floors}</div>
      <div><b>装修面积：</b>${line(d.area, 8)} m² &nbsp;&nbsp;&nbsp;是否涉及整层/整区：${box(d.wholeFloor === '否')}否 &nbsp;${box(d.wholeFloor === '是')}是（面积占比约 ${line(d.areaRatio, 4)}%）</div>
    </div>

    <div class="sec-bar">三、工程概况</div>
    <div class="deco-block">
      <div><b>装修类型：</b>${decoTypes}</div>
      <div><b>涉及内容（可多选）：</b>${contents1}</div>
      <div class="indent-line">${contents2}${line(d.contentOther, 12)}</div>
      <div><b>是否涉及结构变动：</b>${box(d.structureChange === '否')}否 &nbsp;${box(d.structureChange === '是')}是（${structItems}${line(d.structureOther, 6)}）</div>
      <div class="red-warn">※涉及结构变动须附设计院出具的结构安全方案并加盖出图章。</div>
      <div><b>涉及消防设施变动：</b>${box(d.fireChange === '否')}否 &nbsp;${box(d.fireChange === '是')}是（${fireItems}）</div>
      <div><b>计划施工期限：</b>自 ${line(sd.y, 4)}年 ${line(sd.m, 2)}月 ${line(sd.d, 2)}日 至 ${line(ed.y, 4)}年 ${line(ed.m, 2)}月 ${line(ed.d, 2)}日，共 ${line(d.planDays, 4)} 天</div>
    </div>

    <div class="sec-bar">四、施工及设计单位信息</div>
    <div class="deco-block">
      <div><b>施工单位：</b>${line(record?.contractor, 22)} &nbsp;<b>资质证书编号：</b>${line(d.qualNo, 18)}</div>
      <div><b>项目负责人：</b>${line(d.projectLeader, 10)} &nbsp;<b>安全负责人：</b>${line(d.safetyLeader, 10)} &nbsp;<b>特种作业人员数量：</b>${line(d.specialWorkerCount, 5)} 人</div>
      <div><b>设计单位（涉及结构/消防时必填）：</b>${line(d.designCompany, 20)} &nbsp;<b>资质等级：</b>${line(d.designQualLevel, 10)}</div>
    </div>

    <div class="sec-bar">五、申请声明与签署</div>
    <div class="deco-block">
      <div class="indent-text">本单位/本人声明：已阅知《DIPARK 数智云仓产业园装修管理规定》及安全生产责任书全部条款，所提交材料真实完整，承诺严格按批准范围施工，服从园区统一管理。</div>
      <div class="sign-row">
        <span>申请单位（盖章）：${line(d.applicantStampText, 12)}</span>
        <span>法定代表人/授权代表（签字）：${line(d.legalRepSign, 10)}</span>
        <span>日期：${line(d.declareDate, 10)}</span>
      </div>
    </div>

    <div class="sec-bar">六、物业受理登记（物业填写）</div>
    <div class="deco-block">
      <div>受理人：${line(d.receiver, 8)} &nbsp;受理日期：${line(rd.y, 4)}年${line(rd.m, 2)}月${line(rd.d, 2)}日 &nbsp;${box(d.receiveResult === '已受理')}材料齐全已受理 &nbsp;${box(d.receiveResult === '退回')}缺件退回（缺：${line(d.missingDocs, 10)}）</div>
      <div>附件核验：${attachList}</div>
    </div>

    <div class="doc-footer">DIPARK 数智云仓产业园 物业服务中心制 &nbsp;&nbsp;第 1 页／共 2 页（正面·审批联）</div>
    ${sealPage1}
  </div>`

  const page2 = `<div class="permit-page deco-page">
    <div class="doc-header">
      <div class="doc-title">DIPARK 数智云仓产业园装修报批申请表</div>
      <div class="doc-sub">反面 · 审批联（与正面同一编号，缺一不可）</div>
    </div>
    <div class="doc-no right-align">对应正面编号：DIPARK-装批-${line(seq, 8)}号</div>

    <div class="sec-bar">一、分区/楼层相邻方告知确认</div>
    <div class="deco-block compact">
      <div>确认已告知以下相邻区域/楼层使用方：</div>
      <div>${neighborList}</div>
      <div>告知方式：${box(has(d.notifyWays, '书面送达'))}书面《装修施工告知书》已送达（回执见附件） &nbsp;${box(has(d.notifyWays, '现场公示'))}现场公示 &nbsp;${box(has(d.notifyWays, '其他'))}其他${line(d.notifyOther, 10)}</div>
    </div>

    <div class="sec-bar">二、各部门审核意见</div>
    <div class="deco-block compact">
      <div><b>【工程部】</b>结构/水电/暖通/消防可行性审核</div>
      <div>意见：${box(d.engOpinion === '同意')}同意 &nbsp;${box(d.engOpinion === '条件通过')}条件通过（需补充：${line(d.engSupplement, 14)}） &nbsp;${box(d.engOpinion === '退回修改')}退回修改</div>
      <div>签字：${line(d.engSign, 10)} &nbsp;&nbsp;&nbsp;日期：${line(engD.y, 4)}年${line(engD.m, 2)}月${line(engD.d, 2)}日</div>

      <div class="mt6"><b>【安环部】</b>消防安全/环保/危险作业审核</div>
      <div>意见：${box(d.ehsOpinion === '同意')}同意 &nbsp;${box(d.ehsOpinion === '条件通过')}条件通过（需补充：${line(d.ehsSupplement, 14)}） &nbsp;${box(d.ehsOpinion === '退回修改')}退回修改</div>
      <div>签字：${line(d.ehsSign, 10)} &nbsp;&nbsp;&nbsp;日期：${line(ehsD.y, 4)}年${line(ehsD.m, 2)}月${line(ehsD.d, 2)}日</div>

      <div class="mt6"><b>【物业安保】</b>门禁/出入证/施工影响/噪声管控</div>
      <div>意见：${box(d.secOpinion === '同意')}同意 &nbsp;${box(d.secOpinion === '条件通过')}条件通过（需补充：${line(d.secSupplement, 14)}） &nbsp;${box(d.secOpinion === '退回修改')}退回修改</div>
      <div>签字：${line(d.secSign, 10)} &nbsp;&nbsp;&nbsp;日期：${line(secD.y, 4)}年${line(secD.m, 2)}月${line(secD.d, 2)}日</div>

      <div class="mt6"><b>【园区管理方（总审批）】</b></div>
      <div>审批结论：${box(d.parkOpinion === '批准施工')}批准施工 &nbsp;${box(d.parkOpinion === '有条件批准')}有条件批准 &nbsp;${box(d.parkOpinion === '不予批准')}不予批准（理由：${line(d.parkReason, 16)}）</div>
      <div><b>批准施工期限：</b>自 ${line(parkS.y, 4)}年${line(parkS.m, 2)}月${line(parkS.d, 2)}日 至 ${line(parkE.y, 4)}年${line(parkE.m, 2)}月${line(parkE.d, 2)}日</div>
      <div class="mt6">审批人签字：${line(d.parkSign, 12)}（园区负责人） &nbsp;&nbsp;盖章：&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;日期：${line(parkD.y, 4)}年${line(parkD.m, 2)}月${line(parkD.d, 2)}日</div>
    </div>

    <div class="sec-bar">三、开工前条件核验（物业现场核查后勾选）</div>
    <div class="deco-block compact">
      ${preCheckLines}
      <div>${box(has(d.preChecks, 10))} 装修保证金 ${line(d.depositAmount, 8)} 元已缴纳（收据编号：${line(d.depositReceiptNo, 10)}）</div>
      <div>${box(has(d.preChecks, 11))} 施工责任保险已购买（保单号：${line(d.insuranceNo, 14)}）</div>
      <div class="mt4">核验人：${line(d.preVerifier, 10)} &nbsp;日期：${line(preD.y, 4)}年${line(preD.m, 2)}月${line(preD.d, 2)}日 &nbsp;${box(d.preVerifyResult === '准予开工')}准予开工 &nbsp;${box(d.preVerifyResult === '停工整改')}停工整改（事项：${line(d.preRectifyItems, 10)}）</div>
    </div>

    <div class="sec-bar">四、施工管理要点</div>
    <div class="deco-block compact clause-list">
      <div>1. 施工时间：工作日 8:00-18:00，噪声作业限 9:00-12:00/14:00-18:00；</div>
      <div>2. 时效：批准到期须提前 3 天续期，未续自动作废；变更或中止须重新报批；</div>
      <div>3. 分区与楼层管理：不同区域、不同楼层装修分别报批；跨区施工或同区跨层施工须逐层隔离防护；</div>
      <div>4. 材料堆放：限当日用量，严禁占用公共通道、消防通道及相邻楼层；</div>
      <div>5. 废弃物：当日清运出园区；危废（废漆/稀释剂/包装物）交有资质单位处理并留联单；</div>
      <div>6. 竣工：恢复消防设施原状并测试合格，清除全部临时设施与围挡；</div>
      <div>7. 违规：违反任一条款，园区有权停工整改；严重者终止装修许可、扣除保证金。</div>
    </div>

    <div class="sec-bar">五、完工验收与保证金退还</div>
    <div class="deco-block compact">
      <div>报验日期：${line(insAppD.y, 4)}年${line(insAppD.m, 2)}月${line(insAppD.d, 2)}日 &nbsp;&nbsp;实际完工日期：${line(actFinD.y, 4)}年${line(actFinD.m, 2)}月${line(actFinD.d, 2)}日</div>
      <div>验收结论：${box(d.inspectConclusion === '合格')}合格（场地恢复、设施正常） &nbsp;${box(d.inspectConclusion === '不合格')}不合格（整改事项：${line(d.inspectRectify, 18)}）</div>
      <div>验收签字：工程部${line(d.inspectEng, 7)} &nbsp;安环部${line(d.inspectEhs, 7)} &nbsp;物业${line(d.inspectProp, 7)} &nbsp;申请人${line(d.inspectApplicant, 7)} &nbsp;日期：${line(insD.y, 4)}年${line(insD.m, 2)}月${line(insD.d, 2)}日</div>
      <div>保证金退还：${box(d.depositRefundType === '全额退还')}全额退还 ${line(d.refundFullAmount, 7)} 元 &nbsp;${box(d.depositRefundType === '扣除后退还')}扣除 ${line(d.deductAmount, 6)} 元（原因：${line(d.deductReason, 8)}）实退 ${line(d.actualRefundAmount, 7)} 元</div>
    </div>

    <div class="doc-footer">DIPARK 数智云仓产业园 物业服务中心制 &nbsp;&nbsp;第 2 页／共 2 页（反面·验收联）</div>
    ${sealPage2}
  </div>`

  return [page1, page2]
}

export const PERMIT_CSS = `
  * { box-sizing: border-box; }
  body { margin: 0; padding: 0; background: #fff; font-family: "SimSun", "Songti SC", "Microsoft YaHei", serif; color: #111827; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .permit-page {
    position: relative;
    width: 210mm;
    min-height: 296mm;
    padding: 11mm 13mm 11mm;
    margin: 0 auto;
    background: #fff;
    font-size: 10.2pt;
    line-height: 1.52;
    page-break-after: always;
    display: flex;
    flex-direction: column;
  }
  .permit-page:last-child { page-break-after: auto; }
  .doc-header {
    border: 1.5px solid #1e365d;
    background: #f4f6fa;
    padding: 9px 12px 7px;
    text-align: center;
    margin-bottom: 6px;
  }
  .doc-title {
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
    font-size: 17pt;
    font-weight: 800;
    color: #1e365d;
    letter-spacing: 1px;
    line-height: 1.25;
  }
  .doc-sub {
    font-size: 9pt;
    color: #64748b;
    margin-top: 3px;
  }
  .doc-no {
    font-size: 9.5pt;
    color: #1f2937;
    margin: 3px 0 6px;
  }
  .doc-no.flex-between { display: flex; justify-content: space-between; align-items: center; }
  .doc-no.right-align { text-align: right; }
  .sec-bar {
    background: #1e365d;
    color: #fff;
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
    font-size: 10pt;
    font-weight: 700;
    padding: 3px 8px;
    margin: 6px 0 4px;
    line-height: 1.35;
  }
  .sec-bar.center { text-align: center; }
  .grid-table {
    width: 100%;
    border-collapse: collapse;
    margin-bottom: 3px;
    font-size: 9.2pt;
  }
  .grid-table th, .grid-table td {
    border: 1px solid #6b7f9e;
    padding: 3px 6px;
    vertical-align: middle;
    line-height: 1.4;
  }
  .grid-table th {
    background: #e8eef6;
    color: #1e365d;
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
    font-weight: 700;
    text-align: center;
  }
  .hw-info th { width: 15%; }
  .hw-info td { width: 35%; }
  .grid-table td.c { text-align: center; }
  .safety-table td.chk-col { width: 34px; text-align: center; font-size: 10.5pt; }
  .half-table td { width: 50%; }
  .monitor-table td { height: 24px; }
  .tiny-note {
    font-size: 8.2pt;
    color: #4b5563;
    margin: 1px 0 3px;
  }
  .plain-block {
    font-size: 9.2pt;
    line-height: 1.56;
    padding: 2px 2px;
  }
  .gas-meta {
    font-size: 9.2pt;
    margin: 2px 0 4px;
  }
  .sign-line {
    font-size: 9.2pt;
    margin: 4px 0 2px;
  }
  .bold-tip {
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
    font-weight: 700;
    font-size: 8.8pt;
    color: #111827;
    margin-bottom: 3px;
  }
  .approve-list > div { margin-bottom: 3px; }
  .clause-list > div { margin-bottom: 2px; font-size: 9pt; line-height: 1.5; }
  .deco-page .deco-block {
    font-size: 9.4pt;
    line-height: 1.75;
    padding: 2px 3px 4px;
  }
  .deco-page .deco-block.compact {
    font-size: 8.9pt;
    line-height: 1.52;
    padding: 1px 3px 2px;
  }
  .deco-page b {
    font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
    font-weight: 700;
  }
  .indent-line { padding-left: 36px; }
  .indent-text { text-indent: 2em; line-height: 1.65; }
  .red-warn {
    color: #b91c1c;
    font-size: 8.6pt;
    padding-left: 16px;
    line-height: 1.45;
  }
  .sign-row {
    display: flex;
    justify-content: space-between;
    margin-top: 10px;
  }
  .mt4 { margin-top: 4px; }
  .mt6 { margin-top: 6px; }
  .chk {
    display: inline-block;
    font-family: "Segoe UI Symbol", "Microsoft YaHei", sans-serif;
    font-size: 10pt;
    margin-right: 1px;
  }
  .chk.on { font-weight: 700; color: #1e365d; }
  .uline {
    display: inline-block;
    border-bottom: 1px solid #374151;
    padding: 0 3px;
    text-align: center;
    line-height: 1.15;
    vertical-align: baseline;
  }
  .doc-footer {
    margin-top: auto;
    padding-top: 6px;
    text-align: center;
    font-size: 8.4pt;
    color: #6b7280;
  }
  .permit-seal {
    position: absolute;
    z-index: 25;
    pointer-events: none;
    mix-blend-mode: multiply;
    height: auto;
  }
  @media print {
    @page { size: A4 portrait; margin: 0; }
    body { margin: 0; }
    .permit-page { margin: 0; width: 210mm; height: 297mm; min-height: 297mm; overflow: hidden; }
  }
`

export function printPermitForm(record, formData, sealObj = null) {
  const win = window.open('', '_blank')
  if (!win) return false
  const isHot = String(record?.applyType || '').toUpperCase() === 'HOT_WORK'
  const pages = isHot
    ? buildHotWorkPages(record, formData, sealObj)
    : buildDecorationPages(record, formData, sealObj)
  const title = `${isHot ? '动火作业审批表' : '装修报批申请表'} - ${record?.applyNo || ''}`
  win.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${esc(title)}</title><style>${PERMIT_CSS}</style></head><body>${pages.join('')}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => win.print(), 350)
  return true
}
