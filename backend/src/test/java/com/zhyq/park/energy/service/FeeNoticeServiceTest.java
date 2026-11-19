package com.zhyq.park.energy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.energy.entity.FeeNotice;
import com.zhyq.park.energy.entity.FeeNoticeItem;
import com.zhyq.park.energy.entity.FeePeriod;
import com.zhyq.park.energy.entity.FeeSnapshot;
import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.FeeNoticeItemMapper;
import com.zhyq.park.energy.mapper.FeeNoticeMapper;
import com.zhyq.park.energy.mapper.FeePeriodMapper;
import com.zhyq.park.energy.mapper.FeeSnapshotMapper;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.ReadingMapper;
import com.zhyq.park.energy.service.FeeNoticeCalculator.SheetRow;
import com.zhyq.park.finance.entity.Bill;
import com.zhyq.park.finance.mapper.BillMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeNoticeServiceTest {

    @Mock private FeePeriodMapper periodMapper;
    @Mock private FeeNoticeMapper noticeMapper;
    @Mock private FeeNoticeItemMapper itemMapper;
    @Mock private MeterMapper meterMapper;
    @Mock private ReadingMapper readingMapper;
    @Mock private BillMapper billMapper;
    @Mock private JdbcTemplate jdbc;
    @Mock private FeeSnapshotMapper snapshotMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FeeNoticeService service;

    /** LambdaUpdateWrapper.set(...) 需要表信息;单测里没有 Spring 容器,手动登记一次 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> c : new Class<?>[]{FeePeriod.class, FeeNotice.class, Meter.class, Reading.class}) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }

    @BeforeEach
    void setUp() {
        service = new FeeNoticeService(periodMapper, noticeMapper, itemMapper, meterMapper, readingMapper,
                billMapper, jdbc, snapshotMapper, objectMapper);
    }

    private static FeePeriod period(String status) {
        FeePeriod p = new FeePeriod();
        p.setId(1L);
        p.setPeriod("2026-10");
        p.setStatus(status);
        return p;
    }

    private static FeeNotice notice(long id, Long billId) {
        FeeNotice n = new FeeNotice();
        n.setId(id);
        n.setFeePeriodId(1L);
        n.setBillId(billId);
        n.setTotalDue(new BigDecimal("10"));
        return n;
    }

    @Test
    @DisplayName("已确认出账的月份读冻结快照:不再查表计,金额逐分等于确认时的数")
    void confirmedPeriodReadsFrozenSheet() throws Exception {
        FeePeriod p = period(FeePeriod.ST_CONFIRMED);
        when(periodMapper.selectById(1L)).thenReturn(p);
        SheetRow row = new SheetRow();
        row.setMeterId(7L);
        row.setCode("DB-1");
        row.setArea("1层A区");
        row.setEnergyType("电");
        row.setMeterRole("TENANT");
        row.setUsage(new BigDecimal("8003.40"));
        row.setPublicUsage(new BigDecimal("1895.60"));
        row.setFee(new BigDecimal("8243.50"));
        row.setPublicFee(new BigDecimal("1952.47"));
        row.setTotalFee(new BigDecimal("10195.97"));
        FeeNoticeService.Sheet frozen = new FeeNoticeService.Sheet(null, "电", new BigDecimal("176300"), "南网 + 光伏",
                new BigDecimal("8003.40"), new BigDecimal("1895.60"), new BigDecimal("8003.40"), new BigDecimal("1.03"),
                new BigDecimal("10195.97"), List.of(row), List.of("警告"));
        FeeSnapshot snap = new FeeSnapshot();
        snap.setContent(objectMapper.writeValueAsString(frozen));
        when(snapshotMapper.selectOne(any())).thenReturn(snap);

        FeeNoticeService.Sheet read = service.sheet(1L, "电");

        assertThat(read.period()).isSameAs(p);
        assertThat(read.total()).isEqualByComparingTo("176300");
        assertThat(read.totalFee()).isEqualByComparingTo("10195.97");
        assertThat(read.rows()).hasSize(1);
        assertThat(read.rows().get(0).getCode()).isEqualTo("DB-1");
        assertThat(read.rows().get(0).getTotalFee()).isEqualByComparingTo("10195.97");
        verify(meterMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("生成通知单之后又改了读数 → 标记通知单已过期")
    void noticeStaleWhenInputsChangedAfterGenerate() {
        FeePeriod p = period(FeePeriod.ST_DRAFT);
        p.setGeneratedTime(LocalDateTime.of(2026, 11, 1, 10, 0, 0));
        p.setInputsChangedTime(LocalDateTime.of(2026, 11, 1, 10, 5, 0));
        when(periodMapper.selectById(1L)).thenReturn(p);
        assertThat(service.require(1L).getNoticeStale()).isTrue();

        p.setInputsChangedTime(LocalDateTime.of(2026, 11, 1, 9, 59, 0));
        assertThat(service.require(1L).getNoticeStale()).isTrue(); // 旧版草稿要按归属期重算
        p.setCalculationVersion(2);
        assertThat(service.require(1L).getNoticeStale()).isFalse();
    }

    @Test
    @DisplayName("删除通知单:对应账单已收款时拦下,什么都不删")
    void deleteNoticesRefusedWhenBillAlreadyPaid() {
        when(periodMapper.selectById(1L)).thenReturn(period(FeePeriod.ST_CONFIRMED));
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice(11L, 5L)));
        Bill paid = new Bill();
        paid.setId(5L);
        paid.setPaidAmount(new BigDecimal("1.00"));
        when(billMapper.lockActive(5L)).thenReturn(paid);

        assertThatThrownBy(() -> service.deleteNotices(1L, List.of(11L), false))
                .isInstanceOf(BizException.class).hasMessageContaining("已收款");
        verify(billMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(noticeMapper, never()).deleteBatchIds(anyList());
    }

    @Test
    @DisplayName("删除通知单:未收款的连账单一起删,不选任何单据会提示")
    void deleteNoticesRemovesUnpaidBillToo() {
        when(periodMapper.selectById(1L)).thenReturn(period(FeePeriod.ST_DRAFT));
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice(11L, 5L)), List.of());
        Bill unpaid = new Bill();
        unpaid.setId(5L);
        unpaid.setPaidAmount(BigDecimal.ZERO);
        when(billMapper.lockActive(5L)).thenReturn(unpaid);

        var r = service.deleteNotices(1L, List.of(11L), false);

        assertThat(r.get("deleted")).isEqualTo(1);
        assertThat(r.get("bills")).isEqualTo(1);
        verify(billMapper).deleteById(5L);
        verify(noticeMapper).deleteBatchIds(List.of(11L));
        assertThatThrownBy(() -> service.deleteNotices(1L, List.of(), false))
                .isInstanceOf(BizException.class).hasMessageContaining("选择");
    }

    @Test
    @DisplayName("改本月读数时,下个月已确认出账就拦下;下个月是草稿则把上期读数、用量、费用一起改")
    void savingReadingCascadesIntoNextMonthOrRefuses() {
        FeePeriod p = period(FeePeriod.ST_DRAFT);
        p.setElectricPrice(new BigDecimal("1.03"));
        when(periodMapper.selectById(1L)).thenReturn(p);
        Meter meter = new Meter();
        meter.setId(7L);
        meter.setEnergyType("电");
        meter.setRatio(new BigDecimal("30"));
        meter.setLastReading(BigDecimal.ZERO);
        when(meterMapper.selectById(7L)).thenReturn(meter);
        Reading next = new Reading();
        next.setId(99L);
        next.setMeterId(7L);
        next.setPeriod("2026-11");
        next.setPrevReading(new BigDecimal("100"));
        next.setCurrReading(new BigDecimal("160"));
        when(readingMapper.selectOne(any())).thenReturn(null, next);
        FeePeriod nextPeriod = new FeePeriod();
        nextPeriod.setId(2L);
        nextPeriod.setPeriod("2026-11");
        nextPeriod.setStatus(FeePeriod.ST_DRAFT);
        nextPeriod.setElectricPrice(new BigDecimal("1.03"));
        when(periodMapper.selectOne(any())).thenReturn(nextPeriod);

        service.saveReadings(1L, List.of(new FeeNoticeService.ReadingInput(7L, new BigDecimal("100"), new BigDecimal("120"))));

        // 下个月的上期读数 = 本月本期读数 120,用量 = (160 − 120) × 30 = 1200,费用 = 1200 × 1.03
        assertThat(next.getPrevReading()).isEqualByComparingTo("120");
        assertThat(next.getUsageAmount()).isEqualByComparingTo("1200.00");
        assertThat(next.getFee()).isEqualByComparingTo("1236.00");
        verify(readingMapper).updateById(next);

        // 下个月已确认出账 → 不能悄悄改
        nextPeriod.setStatus(FeePeriod.ST_CONFIRMED);
        when(readingMapper.selectOne(any())).thenReturn(null, next);
        assertThatThrownBy(() -> service.saveReadings(1L,
                List.of(new FeeNoticeService.ReadingInput(7L, new BigDecimal("100"), new BigDecimal("125")))))
                .isInstanceOf(BizException.class).hasMessageContaining("已确认出账");
    }

    private static FeeNoticeItem item(long id, String type, String name, String amount, String remark, int manual) {
        FeeNoticeItem it = new FeeNoticeItem();
        it.setId(id);
        it.setNoticeId(3L);
        it.setItemType(type);
        it.setFeeItem(name);
        it.setAmount(amount == null ? null : new BigDecimal(amount));
        it.setRemark(remark);
        it.setManual(manual);
        it.setSort((int) id);
        return it;
    }

    @Test
    @DisplayName("整单调整:改自动行备注 + 整体替换手工收费行,其他费用与应收合计跟着重算")
    void editNoticeReplacesManualItemsAndRecalculates() {
        when(periodMapper.selectById(1L)).thenReturn(period(FeePeriod.ST_DRAFT));
        FeeNotice n = notice(3L, null);
        n.setElectricFee(new BigDecimal("100.00"));
        n.setElectricPublicFee(new BigDecimal("20.00"));
        when(noticeMapper.selectById(3L)).thenReturn(n);
        FeeNoticeItem electric = item(5, FeeNoticeItem.T_ELECTRIC, "电费", "100.00", "自动备注", 0);
        FeeNoticeItem oldOther = item(6, FeeNoticeItem.T_OTHER, "税金", "3.75", null, 1);
        FeeNoticeItem fix = item(901, FeeNoticeItem.T_CUSTOM, "设备维修费", "201.00", null, 1);
        FeeNoticeItem discount = item(902, FeeNoticeItem.T_CUSTOM, "减免", "-50.00", null, 1);
        when(itemMapper.selectList(any())).thenReturn(List.of(electric, oldOther), List.of(electric, fix, discount));

        FeeNotice out = service.editNotice(new FeeNoticeService.NoticeEdit(3L, null, null, new BigDecimal("10"), null,
                "本月含补抄", java.util.Map.of(5L, "手工备注"),
                List.of(new FeeNoticeService.ManualItemInput("设备维修费", null, new BigDecimal("2"), new BigDecimal("100.5"), null, null),
                        new FeeNoticeService.ManualItemInput("减免", null, null, null, new BigDecimal("-50"), "老客户"))));

        assertThat(electric.getRemark()).isEqualTo("手工备注");
        assertThat(electric.getRemarkEdited()).isEqualTo(1);
        verify(itemMapper).deleteById(6L);               // 旧的手工行被整体替换
        verify(itemMapper, times(2)).insert(any(FeeNoticeItem.class));
        assertThat(out.getOtherFee()).isEqualByComparingTo("151.00");   // 201 - 50
        assertThat(out.getOtherFeeNote()).isEqualTo("设备维修费、减免");
        // 小计 100 + 20 + 151 = 271;应收 = 271 - 已付 10
        assertThat(out.getSubtotal()).isEqualByComparingTo("271.00");
        assertThat(out.getTotalDue()).isEqualByComparingTo("261.00");
        assertThat(out.getRemark()).isEqualTo("本月含补抄");
    }

    @Test
    @DisplayName("整单调整:手工行没填名称或金额会被拦下,已出账的月份不能调整")
    void editNoticeValidatesInput() {
        when(noticeMapper.selectById(3L)).thenReturn(notice(3L, null));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(periodMapper.selectById(1L)).thenReturn(period(FeePeriod.ST_DRAFT));

        assertThatThrownBy(() -> service.editNotice(new FeeNoticeService.NoticeEdit(3L, null, null, null, null, null, null,
                List.of(new FeeNoticeService.ManualItemInput(" ", null, null, null, new BigDecimal("1"), null)))))
                .isInstanceOf(BizException.class).hasMessageContaining("费用项目");
        assertThatThrownBy(() -> service.editNotice(new FeeNoticeService.NoticeEdit(3L, null, null, null, null, null, null,
                List.of(new FeeNoticeService.ManualItemInput("维修费", null, null, null, null, null)))))
                .isInstanceOf(BizException.class).hasMessageContaining("金额");

        when(periodMapper.selectById(1L)).thenReturn(period(FeePeriod.ST_CONFIRMED));
        assertThatThrownBy(() -> service.editNotice(new FeeNoticeService.NoticeEdit(3L, null, null, null, null, "x", null, null)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("整单调整的请求体能从 JSON 反序列化(备注 map 的 key 是明细行 id)")
    void noticeEditDeserializes() throws Exception {
        FeeNoticeService.NoticeEdit e = objectMapper.readValue("""
                {"id":3,"paidAmount":0,"remark":"r","itemRemarks":{"5":"手工备注"},
                 "manualItems":[{"feeItem":"维修费","quantity":2,"unitPrice":100.5,"remark":"x"}]}""",
                FeeNoticeService.NoticeEdit.class);
        assertThat(e.itemRemarks()).containsEntry(5L, "手工备注");
        assertThat(e.manualItems().get(0).unitPrice()).isEqualByComparingTo("100.5");
    }
}
