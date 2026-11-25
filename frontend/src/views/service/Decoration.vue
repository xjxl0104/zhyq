<template>
  <div class="page-container">
    <!-- 顶部类型切换 + 统计 -->
    <div class="top-tabs-bar">
      <el-radio-group v-model="query.applyType" @change="onTypeChange">
        <el-radio-button value="">全部申请</el-radio-button>
        <el-radio-button value="DECORATION">装修报批申请表</el-radio-button>
        <el-radio-button value="HOT_WORK">动火作业审批表</el-radio-button>
      </el-radio-group>
      <div class="create-actions">
        <el-button type="primary" @click="openForm('DECORATION')"><el-icon><Plus /></el-icon>新建装修报批申请表</el-button>
        <el-button type="danger" @click="openForm('HOT_WORK')"><el-icon><Plus /></el-icon>新建动火作业审批表</el-button>
      </div>
    </div>

    <!-- 查询区 -->
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="编号 / 申请单位 / 施工单位 / 区域" clearable style="width: 240px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(label, val) in statusMap" :key="val" :label="label" :value="Number(val)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 列表区 -->
    <div class="table-card">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="申请类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="isHot(row) ? 'danger' : 'primary'" effect="light">
              {{ isHot(row) ? '动火作业审批' : '装修报批申请' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyNo" label="申请表编号" width="175" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applyNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="applicantUnit" label="申请单位 / 租户" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applicantUnit || tenantNameById(row.tenantRefId) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="workArea" label="部位 / 作业区域" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.workArea || row.scope || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contractor" label="施工单位 / 负责人" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contractor || row.contact || '-' }}</template>
        </el-table-column>
        <el-table-column label="计划期限" width="195">
          <template #default="{ row }">
            <span v-if="row.startDate || row.endDate">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</span>
            <span v-else>{{ row.applyDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="电子印章" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.sealId" size="small" type="danger" effect="plain">已盖章</el-tag>
            <span v-else class="muted">未盖章</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusMap[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row)">生成/盖章打印</el-button>
            <el-button link type="primary" @click="openForm(row.applyType || 'DECORATION', row)">填写编辑</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="approve(row.id)">通过</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="reject(row.id)">驳回</el-button>
            <el-button v-if="row.status === 2" link type="success" @click="finish(row.id)">完工</el-button>
            <el-popconfirm title="确认删除该申请表?" @confirm="remove(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" background layout="total, prev, pager, next, sizes"
                     :total="total" v-model:current-page="query.pageNo"
                     v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50]" @change="load" />
    </div>

    <!-- 填写装修报批 / 动火作业申请表弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="920px" top="3vh" destroy-on-close>
      <el-tabs v-model="activeTab" type="border-card">
        <!-- ==================== 装修报批申请表 ==================== -->
        <template v-if="form.applyType === 'DECORATION'">
          <el-tab-pane label="正面：申请信息与工程概况" name="p1">
            <el-form :model="form" label-width="116px" size="small">
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="申请表编号">
                    <el-input v-model="form.applyNo" placeholder="留空自动生成，如 DIPARK-装批-2026001" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="申请日期">
                    <el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="选择园区租户">
                    <el-select v-model="form.tenantRefId" placeholder="可选：选择已有租户自动带出" filterable clearable style="width: 100%" @change="onTenantPick">
                      <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="承租单位(全称)">
                    <el-input v-model="form.applicantUnit" placeholder="承租单位全称" />
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="统一社会信用代码"><el-input v-model="deco.creditCode" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="联系人" label-width="64px"><el-input v-model="form.contact" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="电话" label-width="52px"><el-input v-model="form.phone" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="实际使用人"><el-input v-model="deco.actualUser" placeholder="若与承租方不同填写" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="使用人电话"><el-input v-model="deco.actualUserPhone" /></el-form-item></el-col>
              </el-row>

              <el-divider content-position="left">二、装修部位（按分区·楼层定位）</el-divider>
              <el-form-item label="所在区">
                <el-checkbox-group v-model="deco.zones">
                  <el-checkbox v-for="z in ['A区', 'B区', 'C区']" :key="z" :value="z">{{ z }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="楼层">
                <el-checkbox-group v-model="deco.floors">
                  <el-checkbox v-for="f in ['1F','2F','3F','4F','5F','6F','7F','夹层','屋面/天台']" :key="f" :value="f">{{ f }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="8"><el-form-item label="装修面积(m²)"><el-input v-model="deco.area" placeholder="如 1200" /></el-form-item></el-col>
                <el-col :span="8">
                  <el-form-item label="整层/整区">
                    <el-radio-group v-model="deco.wholeFloor">
                      <el-radio value="否">否</el-radio>
                      <el-radio value="是">是</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="8"><el-form-item label="面积占比(%)"><el-input v-model="deco.areaRatio" placeholder="如 100" /></el-form-item></el-col>
              </el-row>

              <el-divider content-position="left">三、工程概况</el-divider>
              <el-form-item label="装修类型">
                <el-radio-group v-model="deco.decoType">
                  <el-radio v-for="t in ['首次装修','翻新改造','扩建加层','局部修缮','设备安装调试']" :key="t" :value="t">{{ t }}</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="涉及内容">
                <el-checkbox-group v-model="deco.contents">
                  <el-checkbox v-for="c in ['地面','墙面','天花吊顶','隔断','水电改造','暖通消防','外立面','门窗更换','电梯','货架/重型设备','其他']" :key="c" :value="c">{{ c }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item v-if="deco.contents.includes('其他')" label="其他内容说明">
                <el-input v-model="deco.contentOther" placeholder="请说明其他涉及内容" />
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item label="涉及结构变动">
                    <el-radio-group v-model="deco.structureChange">
                      <el-radio value="否">否</el-radio>
                      <el-radio value="是">是</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="16" v-if="deco.structureChange === '是'">
                  <el-form-item label="结构变动项" label-width="90px">
                    <el-checkbox-group v-model="deco.structureItems">
                      <el-checkbox v-for="s in ['承重墙开洞','梁/柱变动','楼板开孔','屋面荷载增加','其他']" :key="s" :value="s">{{ s }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item label="涉及消防变动">
                    <el-radio-group v-model="deco.fireChange">
                      <el-radio value="否">否</el-radio>
                      <el-radio value="是">是</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="16" v-if="deco.fireChange === '是'">
                  <el-form-item label="消防变动项" label-width="90px">
                    <el-checkbox-group v-model="deco.fireItems">
                      <el-checkbox v-for="f in ['喷淋','烟感/报警','防火卷帘','疏散通道','消火栓','防排烟']" :key="f" :value="f">{{ f }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :span="9"><el-form-item label="计划开工日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="calcPlanDays" /></el-form-item></el-col>
                <el-col :span="9"><el-form-item label="计划完工日期"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="calcPlanDays" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="共(天)" label-width="60px"><el-input v-model="deco.planDays" /></el-form-item></el-col>
              </el-row>

              <el-divider content-position="left">四、施工及设计单位信息 / 五~六、声明与物业受理</el-divider>
              <el-row :gutter="12">
                <el-col :span="12"><el-form-item label="施工单位"><el-input v-model="form.contractor" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="资质证书编号"><el-input v-model="deco.qualNo" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="项目负责人"><el-input v-model="deco.projectLeader" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="安全负责人"><el-input v-model="deco.safetyLeader" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="特种作业人数"><el-input v-model="deco.specialWorkerCount" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="设计单位"><el-input v-model="deco.designCompany" placeholder="涉及结构/消防时必填" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="设计资质等级"><el-input v-model="deco.designQualLevel" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="法定代表/授权人"><el-input v-model="deco.legalRepSign" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="签署日期"><el-date-picker v-model="deco.declareDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="物业受理人"><el-input v-model="deco.receiver" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="受理日期"><el-date-picker v-model="deco.receiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="16">
                  <el-form-item label="受理结论">
                    <el-radio-group v-model="deco.receiveResult">
                      <el-radio value="已受理">材料齐全已受理</el-radio>
                      <el-radio value="退回">缺件退回</el-radio>
                    </el-radio-group>
                    <el-input v-if="deco.receiveResult === '退回'" v-model="deco.missingDocs" placeholder="缺件清单" style="width: 180px; margin-left: 10px" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="附件核验">
                <el-checkbox-group v-model="deco.attachChecks">
                  <el-checkbox v-for="a in ['装修方案','效果图/平面图','施工/设计资质','人员证照','材料清单（燃烧性能）','消防报审','保险单']" :key="a" :value="a">{{ a }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="反面：审批意见、开工核验与验收" name="p2">
            <el-form label-width="116px" size="small">
              <el-form-item label="相邻方告知">
                <el-checkbox-group v-model="deco.neighbors">
                  <el-checkbox v-for="n in ['同区同层相邻租户/使用人','同区上一层','同区下一层','相邻区（A／B／C 区之间）使用人','涉及消防通道通行方','物业管理处']" :key="n" :value="n">{{ n }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-form-item label="告知方式">
                <el-checkbox-group v-model="deco.notifyWays">
                  <el-checkbox value="书面送达">书面《装修施工告知书》已送达</el-checkbox>
                  <el-checkbox value="现场公示">现场公示</el-checkbox>
                  <el-checkbox value="其他">其他</el-checkbox>
                </el-checkbox-group>
              </el-form-item>

              <el-divider content-position="left">二、各部门审核意见</el-divider>
              <el-row :gutter="10">
                <el-col :span="10"><el-form-item label="工程部意见"><el-radio-group v-model="deco.engOpinion"><el-radio value="同意">同意</el-radio><el-radio value="条件通过">条件通过</el-radio><el-radio value="退回修改">退回</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="签字" label-width="48px"><el-input v-model="deco.engSign" /></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="日期" label-width="48px"><el-date-picker v-model="deco.engDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>

                <el-col :span="10"><el-form-item label="安环部意见"><el-radio-group v-model="deco.ehsOpinion"><el-radio value="同意">同意</el-radio><el-radio value="条件通过">条件通过</el-radio><el-radio value="退回修改">退回</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="签字" label-width="48px"><el-input v-model="deco.ehsSign" /></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="日期" label-width="48px"><el-date-picker v-model="deco.ehsDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>

                <el-col :span="10"><el-form-item label="物业安保意见"><el-radio-group v-model="deco.secOpinion"><el-radio value="同意">同意</el-radio><el-radio value="条件通过">条件通过</el-radio><el-radio value="退回修改">退回</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="签字" label-width="48px"><el-input v-model="deco.secSign" /></el-form-item></el-col>
                <el-col :span="7"><el-form-item label="日期" label-width="48px"><el-date-picker v-model="deco.secDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>

                <el-col :span="12"><el-form-item label="园区总审批"><el-radio-group v-model="deco.parkOpinion"><el-radio value="批准施工">批准施工</el-radio><el-radio value="有条件批准">有条件批准</el-radio><el-radio value="不予批准">不予批准</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="签字" label-width="48px"><el-input v-model="deco.parkSign" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="日期" label-width="48px"><el-date-picker v-model="deco.parkDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="批准施工起"><el-date-picker v-model="deco.approvedStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="批准施工止"><el-date-picker v-model="deco.approvedEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              </el-row>

              <el-divider content-position="left">三、开工前条件核验 & 五、完工验收</el-divider>
              <el-form-item label="已核验达标项">
                <el-checkbox-group v-model="deco.preChecks">
                  <el-checkbox v-for="(lbl, idx) in decoPreCheckLabels" :key="idx" :value="idx + 1">{{ lbl }}</el-checkbox>
                </el-checkbox-group>
              </el-form-item>
              <el-row :gutter="10">
                <el-col :span="8"><el-form-item label="装修保证金(元)"><el-input v-model="deco.depositAmount" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="收据编号"><el-input v-model="deco.depositReceiptNo" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="责任险保单号"><el-input v-model="deco.insuranceNo" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="开工核验人"><el-input v-model="deco.preVerifier" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="核验日期"><el-date-picker v-model="deco.preVerifyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="开工结论"><el-radio-group v-model="deco.preVerifyResult"><el-radio value="准予开工">准予开工</el-radio><el-radio value="停工整改">停工整改</el-radio></el-radio-group></el-form-item></el-col>
              </el-row>
              <el-row :gutter="10">
                <el-col :span="8"><el-form-item label="报验日期"><el-date-picker v-model="deco.inspectApplyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="实际完工"><el-date-picker v-model="deco.actualFinishDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="验收结论"><el-radio-group v-model="deco.inspectConclusion"><el-radio value="合格">合格</el-radio><el-radio value="不合格">不合格</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="保证金退还"><el-radio-group v-model="deco.depositRefundType"><el-radio value="全额退还">全额退还</el-radio><el-radio value="扣除后退还">扣除后退还</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="退还/实退金额"><el-input v-model="deco.refundFullAmount" placeholder="元" /></el-form-item></el-col>
              </el-row>
            </el-form>
          </el-tab-pane>
        </template>

        <!-- ==================== 动火作业审批表 ==================== -->
        <template v-else>
          <el-tab-pane label="正面：作业基本信息、核验与审批" name="p1">
            <el-form :model="form" label-width="112px" size="small">
              <el-row :gutter="12">
                <el-col :span="12"><el-form-item label="动火编号"><el-input v-model="form.applyNo" placeholder="留空自动生成，如 〔2026〕动字第001号" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="申请日期"><el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12">
                  <el-form-item label="选择园区租户">
                    <el-select v-model="form.tenantRefId" placeholder="可选：选择租户" filterable clearable style="width: 100%" @change="onTenantPick">
                      <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="申请单位/租户"><el-input v-model="form.applicantUnit" placeholder="申请单位或租户名称" /></el-form-item></el-col>
                <el-col :span="12">
                  <el-form-item label="动火级别">
                    <el-radio-group v-model="hw.fireLevel">
                      <el-radio value="特级">特级</el-radio>
                      <el-radio value="一级">一级</el-radio>
                      <el-radio value="二级">二级</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="动火方式">
                    <el-checkbox-group v-model="hw.fireMethods">
                      <el-checkbox v-for="m in ['电焊','气割','角磨机','其他']" :key="m" :value="m">{{ m }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="作业楼层">
                    <el-checkbox-group v-model="hw.floors">
                      <el-checkbox v-for="f in ['1','2','3','4','5','6','7','屋面']" :key="f" :value="f">{{ f }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="作业分区">
                    <el-checkbox-group v-model="hw.zones">
                      <el-checkbox v-for="z in ['A区','B区','C区','公共/通道']" :key="z" :value="z">{{ z }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="动火开始时间"><el-date-picker v-model="hw.startDateTime" type="datetime" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="动火结束时间"><el-date-picker v-model="hw.endDateTime" type="datetime" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="动火点位"><el-input v-model="hw.fireLocation" placeholder="具体动火位置" /></el-form-item></el-col>
                <el-col :span="12">
                  <el-form-item label="密闭空间">
                    <el-radio-group v-model="hw.confinedSpace"><el-radio value="有">有</el-radio><el-radio value="无">无</el-radio></el-radio-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="主要风险辨识">
                    <el-checkbox-group v-model="hw.risks">
                      <el-checkbox v-for="r in ['易燃物积聚','气瓶泄漏','高处飞火','其他']" :key="r" :value="r">{{ r }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="交叉作业">
                    <el-checkbox-group v-model="hw.crossWorks">
                      <el-checkbox v-for="c in ['高处','受限空间','临时用电','动土']" :key="c" :value="c">{{ c }}</el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </el-col>
                <el-col :span="8"><el-form-item label="施工单位"><el-input v-model="form.contractor" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="作业负责人"><el-input v-model="hw.workLeader" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="负责人电话"><el-input v-model="hw.workLeaderPhone" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="动火作业人"><el-input v-model="hw.operatorName" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="特种作业证号"><el-input v-model="hw.certNo" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="现场监火人"><el-input v-model="hw.watcherName" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="监火人电话"><el-input v-model="hw.watcherPhone" /></el-form-item></el-col>
              </el-row>

              <el-divider content-position="left">二~四、资质核验、可燃气体检测与安全措施</el-divider>
              <el-row :gutter="10">
                <el-col :span="8"><el-form-item label="操作证有效"><el-radio-group v-model="hw.verify1"><el-radio value="是">是</el-radio><el-radio value="否">否</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="人证一致"><el-radio-group v-model="hw.verify2"><el-radio value="是">是</el-radio><el-radio value="否">否</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="审批有效"><el-radio-group v-model="hw.verify3"><el-radio value="是">是</el-radio><el-radio value="否">否</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="检测时间"><el-input v-model="hw.gasTime" placeholder="如 09:30" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="检测部位"><el-input v-model="hw.gasPart" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="检测气体"><el-input v-model="hw.gasType" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="浓度%LEL"><el-input v-model="hw.gasConcentration" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="检测结论"><el-radio-group v-model="hw.gasConclusion"><el-radio value="合格">合格，准许动火</el-radio><el-radio value="不合格">不合格，禁止动火</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="安保核验签字"><el-input v-model="hw.securityVerifySign" /></el-form-item></el-col>
              </el-row>
              <el-form-item label="安全措施勾选">
                <el-checkbox-group v-model="hw.safetyChecks">
                  <el-checkbox v-for="i in 10" :key="i" :value="i">第{{ i }}项已落实</el-checkbox>
                </el-checkbox-group>
                <el-button link type="primary" size="small" @click="hw.safetyChecks = [1,2,3,4,5,6,7,8,9,10]">全选 1~10 项</el-button>
              </el-form-item>

              <el-divider content-position="left">五、逐级审批意见</el-divider>
              <el-row :gutter="10">
                <el-col :span="12"><el-form-item label="1.申请方负责人"><el-radio-group v-model="hw.approve1Choice"><el-radio value="同意">同意动火</el-radio><el-radio value="不同意">暂不动火</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="签字/日期"><el-input v-model="hw.approve1Sign" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="2.物业安全主管"><el-radio-group v-model="hw.approve2Choice"><el-radio value="同意">同意动火</el-radio><el-radio value="不同意">不同意</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="签字/日期"><el-input v-model="hw.approve2Sign" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="3.工程/消防主管"><el-radio-group v-model="hw.approve3Choice"><el-radio value="同意">同意</el-radio><el-radio value="不同意">不同意</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="签字/日期"><el-input v-model="hw.approve3Sign" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="4.项目经理终审"><el-radio-group v-model="hw.approve4Choice"><el-radio value="批准">批准动火</el-radio><el-radio value="不批准">不批准</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="签字/日期"><el-input v-model="hw.approve4Sign" /></el-form-item></el-col>
              </el-row>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="反面：完工验收与现场监护记录" name="p2">
            <el-form label-width="112px" size="small">
              <el-form-item label="完工恢复确认">
                <el-checkbox-group v-model="hw.finishChecks">
                  <el-checkbox v-for="i in 8" :key="i" :value="i">第{{ i }}项确认</el-checkbox>
                </el-checkbox-group>
                <el-button link type="primary" size="small" @click="hw.finishChecks = [1,2,3,4,5,6,7,8]">全选 1~8 项</el-button>
              </el-form-item>
              <el-row :gutter="10">
                <el-col :span="8"><el-form-item label="完工时间"><el-input v-model="hw.finishTime" placeholder="如 17:30" /></el-form-item></el-col>
                <el-col :span="16"><el-form-item label="验收结论"><el-radio-group v-model="hw.finishConclusion"><el-radio value="合格">合格，现场已恢复</el-radio><el-radio value="不合格">不合格，须整改</el-radio></el-radio-group></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="监火人" label-width="62px"><el-input v-model="hw.finishWatcher" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="施工负责" label-width="70px"><el-input v-model="hw.finishLeader" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="安保验收" label-width="70px"><el-input v-model="hw.finishSecurity" /></el-form-item></el-col>
                <el-col :span="6"><el-form-item label="物业复核" label-width="70px"><el-input v-model="hw.finishProperty" /></el-form-item></el-col>
              </el-row>
              <el-divider content-position="left">八、现场监护记录（每 30 分钟记录一次）</el-divider>
              <el-table :data="hw.monitorRows" border size="small">
                <el-table-column label="记录时间" width="120"><template #default="{ row }"><el-input v-model="row.time" size="small" placeholder="10:00" /></template></el-table-column>
                <el-table-column label="动火部位"><template #default="{ row }"><el-input v-model="row.part" size="small" /></template></el-table-column>
                <el-table-column label="现场情况" width="145">
                  <template #default="{ row }">
                    <el-radio-group v-model="row.status" size="small"><el-radio value="正常">正常</el-radio><el-radio value="异常">异常</el-radio></el-radio-group>
                  </template>
                </el-table-column>
                <el-table-column label="气体复测%LEL" width="120"><template #default="{ row }"><el-input v-model="row.gas" size="small" /></template></el-table-column>
                <el-table-column label="监火人签字" width="120"><template #default="{ row }"><el-input v-model="row.sign" size="small" /></template></el-table-column>
              </el-table>
            </el-form>
          </el-tab-pane>
        </template>

        <el-tab-pane label="随表附件材料" name="files">
          <FileUpload v-model="attachFiles" biz-type="decoration" :biz-id="form.id" />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button :loading="saving" @click="submit(false)">仅保存</el-button>
        <el-button type="primary" :loading="saving" @click="submit(true)">保存并生成申请表（盖章/打印）</el-button>
      </template>
    </el-dialog>

    <!-- 申请表生成、盖章拖拽与打印预览弹窗 -->
    <PermitPreviewDialog
      v-model="previewDlg.visible"
      :record="previewDlg.record"
      :seals="seals"
      @saved="onStampSaved"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { decorationApi } from '@/api/service'
import { sealApi } from '@/api/oa'
import { fileApi } from '@/api/file'
import { tenantApi } from '@/api/tenant'
import FileUpload from '@/components/FileUpload.vue'
import PermitPreviewDialog from './components/PermitPreviewDialog.vue'
import { defaultDecorationData, defaultHotWorkData } from './permitPrint'

const statusMap = { 1: '待审批', 2: '施工中', 3: '已完工', 4: '已驳回' }
const statusTagType = (s) => ({ 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[s] || 'info')
const isHot = (row) => String(row?.applyType || '').toUpperCase() === 'HOT_WORK'

const decoPreCheckLabels = [
  '1.燃烧性能达标', '2.特种证照有效', '3.出入证及安全教育', '4.临时用电合格',
  '5.动火票/灭火器就位', '6.高处防坠落到位', '7.硬质围挡有效隔离',
  '8.消防通道无遮挡', '9.噪声粉尘管控落实', '10.装修保证金已缴', '11.施工责任险已购'
]

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const tenants = ref([])
const seals = ref([])
const query = reactive({ pageNo: 1, pageSize: 10, applyType: '', status: null, keyword: '' })

const tenantNameById = (id) => tenants.value.find((t) => String(t.id) === String(id))?.name || ''

async function loadMeta() {
  try { tenants.value = (await tenantApi.list()) || [] } catch { /* ignore */ }
  try { seals.value = (await sealApi.list({ onlyEnabled: true })) || [] } catch { /* ignore */ }
}

async function load() {
  loading.value = true
  try {
    const res = await decorationApi.page(query)
    list.value = res?.records || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function onTypeChange() {
  query.pageNo = 1
  load()
}

function reset() {
  Object.assign(query, { pageNo: 1, applyType: '', status: null, keyword: '' })
  load()
}

const dialog = reactive({ visible: false })
const activeTab = ref('p1')
const attachFiles = ref([])

const emptyBase = (type) => ({
  id: null,
  applyType: type || 'DECORATION',
  applyNo: '',
  applicantUnit: '',
  applyDate: new Date().toISOString().slice(0, 10),
  workArea: '',
  tenantRefId: null,
  roomId: null,
  contractor: '',
  contact: '',
  phone: '',
  startDate: null,
  endDate: null,
  scope: '',
  sealId: null,
  sealConfigJson: null,
  status: 1
})

const form = reactive(emptyBase('DECORATION'))
const deco = reactive(defaultDecorationData())
const hw = reactive(defaultHotWorkData())

const dialogTitle = computed(() => {
  const label = form.applyType === 'HOT_WORK' ? '动火作业审批表' : '装修报批申请表'
  return `${form.id ? '编辑' : '新建'}${label}`
})

function onTenantPick(id) {
  const t = tenants.value.find((item) => String(item.id) === String(id))
  if (t && !form.applicantUnit) form.applicantUnit = t.name
}

function calcPlanDays() {
  if (!form.startDate || !form.endDate) return
  const diff = Math.round((new Date(form.endDate) - new Date(form.startDate)) / 86400000) + 1
  if (diff > 0) deco.planDays = String(diff)
}

async function openForm(type, row = null) {
  const targetType = row?.applyType || type || 'DECORATION'
  Object.assign(form, emptyBase(targetType))
  Object.assign(deco, defaultDecorationData())
  Object.assign(hw, defaultHotWorkData())
  attachFiles.value = []
  activeTab.value = 'p1'

  if (row) {
    Object.assign(form, { ...emptyBase(targetType), ...row, applyType: targetType })
    let parsed = {}
    if (row.formDataJson) {
      try { parsed = JSON.parse(row.formDataJson) } catch { parsed = {} }
    }
    if (targetType === 'HOT_WORK') Object.assign(hw, defaultHotWorkData(), parsed)
    else Object.assign(deco, defaultDecorationData(), parsed)
    try { attachFiles.value = (await fileApi.list('decoration', row.id)) || [] } catch { /* ignore */ }
  }
  dialog.visible = true
}

function buildWorkAreaSummary() {
  if (form.applyType === 'HOT_WORK') {
    const parts = [
      hw.floors?.length ? `${hw.floors.join('/')}层` : '',
      hw.zones?.length ? hw.zones.join('/') : '',
      hw.fireLocation || ''
    ].filter(Boolean)
    return parts.join(' · ')
  }
  const parts = [
    deco.zones?.length ? deco.zones.join('/') : '',
    deco.floors?.length ? deco.floors.join('/') : '',
    deco.area ? `${deco.area}㎡` : ''
  ].filter(Boolean)
  return parts.join(' · ')
}

async function submit(openAfterSave) {
  if (!form.applicantUnit && !form.contractor) {
    return ElMessage.warning('请至少填写「申请单位/租户」或「施工单位」')
  }
  saving.value = true
  try {
    const workArea = buildWorkAreaSummary() || form.workArea
    const payload = {
      ...form,
      workArea,
      scope: form.scope || workArea || (form.applyType === 'HOT_WORK' ? (hw.fireMethods || []).join('、') : (deco.contents || []).join('、')),
      contact: form.applyType === 'HOT_WORK' ? (hw.workLeader || form.contact) : form.contact,
      phone: form.applyType === 'HOT_WORK' ? (hw.workLeaderPhone || form.phone) : form.phone,
      startDate: form.applyType === 'HOT_WORK' ? (hw.startDateTime ? hw.startDateTime.slice(0, 10) : form.startDate) : form.startDate,
      endDate: form.applyType === 'HOT_WORK' ? (hw.endDateTime ? hw.endDateTime.slice(0, 10) : form.endDate) : form.endDate,
      formDataJson: JSON.stringify(form.applyType === 'HOT_WORK' ? hw : deco)
    }
    let id = form.id
    if (id) {
      await decorationApi.update(payload)
    } else {
      id = await decorationApi.add(payload)
    }
    const pendingIds = (attachFiles.value || []).filter((f) => f && f.id && !f.bizId).map((f) => f.id)
    if (id && pendingIds.length) {
      try { await fileApi.attach('decoration', id, pendingIds) } catch { /* ignore */ }
    }
    ElMessage.success('申请表已保存')
    dialog.visible = false
    await load()
    if (openAfterSave && id) {
      const fresh = await decorationApi.get(id)
      if (fresh) openPreview(fresh)
    }
  } finally {
    saving.value = false
  }
}

const previewDlg = reactive({ visible: false, record: null })
async function openPreview(row) {
  if (!seals.value.length) {
    try { seals.value = (await sealApi.list({ onlyEnabled: true })) || [] } catch { /* ignore */ }
  }
  previewDlg.record = row
  previewDlg.visible = true
}
function onStampSaved(updated) {
  const idx = list.value.findIndex((r) => r.id === updated.id)
  if (idx >= 0) list.value[idx] = { ...list.value[idx], ...updated }
  previewDlg.record = { ...previewDlg.record, ...updated }
}

async function approve(id) { await decorationApi.approve(id); ElMessage.success('已审批通过'); load() }
async function reject(id) { await decorationApi.reject(id); ElMessage.success('已驳回'); load() }
async function finish(id) { await decorationApi.finish(id); ElMessage.success('已标记完工'); load() }
async function remove(id) { await decorationApi.remove(id); ElMessage.success('已删除'); load() }

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<style scoped>
.top-tabs-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid #ebeef5;
}
.create-actions {
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
