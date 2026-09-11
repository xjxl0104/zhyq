package com.zhyq.park.energy.controller;

import com.zhyq.park.energy.entity.Meter;
import com.zhyq.park.energy.entity.Reading;
import com.zhyq.park.energy.mapper.MeterMapper;
import com.zhyq.park.energy.mapper.ReadingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 抄表用量口径回归:用量 = (本次 - 上次) × 表计倍率。
 *
 * <p>2026-09-11 口径变更 —— 此前不乘倍率,而园区电表倍率普遍为 30(互感器变比),
 * 不乘会让用电量差 30 倍。本测试把倍率生效钉死为回归锁。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingUsageRatioTest {

    @Mock
    private ReadingMapper readingMapper;

    @Mock
    private MeterMapper meterMapper;

    private ReadingController controller() {
        return new ReadingController(readingMapper, meterMapper);
    }

    private void givenRatio(String ratio) {
        Meter m = new Meter();
        m.setId(1L);
        m.setRatio(ratio == null ? null : new BigDecimal(ratio));
        when(meterMapper.selectById(anyLong())).thenReturn(m);
    }

    private Reading reading(String prev, String curr) {
        Reading r = new Reading();
        r.setMeterId(1L);
        r.setPrevReading(new BigDecimal(prev));
        r.setCurrReading(new BigDecimal(curr));
        return r;
    }

    @Test
    @DisplayName("电表倍率30:真实抄表数据 (439.92-216.03)×30 = 6716.70 度")
    void electricityMeterAppliesRatio30() {
        givenRatio("30");
        // 取自园区 2026-08 抄表表:1层A区 云帕,7月 216.03、8月 439.92
        assertEquals(0, new BigDecimal("6716.70")
                .compareTo(controller().calcUsage(reading("216.03", "439.92"))));
    }

    @Test
    @DisplayName("水表倍率1:用量就是读数差,不放大")
    void waterMeterRatioOneKeepsDifference() {
        givenRatio("1");
        // 1层A区 云帕水表:7月 23、8月 77.5
        assertEquals(0, new BigDecimal("54.5")
                .compareTo(controller().calcUsage(reading("23", "77.5"))));
    }

    @Test
    @DisplayName("读数倒退(换表/抄错)用量取0,不出负数")
    void negativeDifferenceClampedToZero() {
        givenRatio("30");
        assertEquals(0, BigDecimal.ZERO
                .compareTo(controller().calcUsage(reading("500", "100"))));
    }

    @Test
    @DisplayName("倍率缺失按1处理,不把用量清零")
    void missingRatioFallsBackToOne() {
        givenRatio(null);
        assertEquals(0, new BigDecimal("100")
                .compareTo(controller().calcUsage(reading("100", "200"))));
    }

    @Test
    @DisplayName("倍率为0等非法值按1处理,避免历史脏数据清零用量")
    void zeroRatioFallsBackToOne() {
        givenRatio("0");
        assertEquals(0, new BigDecimal("100")
                .compareTo(controller().calcUsage(reading("100", "200"))));
    }

    @Test
    @DisplayName("meterId 为空时按倍率1,不抛异常(PUT 只传部分字段的路径)")
    void nullMeterIdFallsBackToOne() {
        Reading r = new Reading();
        r.setPrevReading(new BigDecimal("10"));
        r.setCurrReading(new BigDecimal("35"));
        assertEquals(0, new BigDecimal("25").compareTo(controller().calcUsage(r)));
    }

    @Test
    @DisplayName("读数为空按0计,不抛 NPE")
    void nullReadingsTreatedAsZero() {
        givenRatio("30");
        Reading r = new Reading();
        r.setMeterId(1L);
        assertEquals(0, BigDecimal.ZERO.compareTo(controller().calcUsage(r)));
    }

    @Test
    @DisplayName("用量标度归一到2位小数")
    void usageScaledToTwoDecimals() {
        givenRatio("1.5");
        // 0.333 x 1.5 = 0.4995 -> 0.50
        assertEquals("0.50", controller().calcUsage(reading("0", "0.333")).toPlainString());
    }

    @Test
    @DisplayName("表计查不到时按倍率1,不抛异常")
    void unknownMeterFallsBackToOne() {
        when(meterMapper.selectById(anyLong())).thenReturn(null);
        assertEquals(0, new BigDecimal("25")
                .compareTo(controller().calcUsage(reading("10", "35"))));
    }
}
