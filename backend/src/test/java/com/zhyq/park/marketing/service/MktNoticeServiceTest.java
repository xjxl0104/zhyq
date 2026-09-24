package com.zhyq.park.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktNotice;
import com.zhyq.park.marketing.mapper.MktNoticeMapper;
import com.zhyq.park.marketing.support.WrapperAssert;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** P2-3 通知:推送落库、未读统计、按 warehouse 隔离、越权标记已读无效。 */
@ExtendWith(MockitoExtension.class)
class MktNoticeServiceTest {
    @Mock MktNoticeMapper mapper;
    MktNoticeService service;

    @BeforeAll static void initMp() {
        MapperBuilderAssistant a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(a, MktNotice.class);
    }

    @BeforeEach void setUp() { service = new MktNoticeService(mapper); }

    @Test void pushInsertsScopedToWarehouse() {
        when(mapper.insert(any(MktNotice.class))).thenReturn(1);
        service.push(11L, "settlement.confirmed", "标题", "内容", "settlement", 5L);
        ArgumentCaptor<MktNotice> cap = ArgumentCaptor.forClass(MktNotice.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getWarehouseId()).isEqualTo(11L);
        assertThat(cap.getValue().getType()).isEqualTo("settlement.confirmed");
        assertThat(cap.getValue().getReadAt()).isNull();
    }

    @Test void pushSwallowsFailureSoBusinessTransactionSurvives() {
        when(mapper.insert(any(MktNotice.class))).thenThrow(new RuntimeException("db down"));
        service.push(11L, "bill.ready", "t", "c", "bill", 1L);   // 不应抛
        verify(mapper).insert(any(MktNotice.class));
    }

    @Test void pushWithNullWarehouseIsNoop() {
        service.push(null, "x", "t", "c", null, null);
        verifyNoInteractions(mapper);
    }

    @Test void markReadIsScopedByWarehouse() {
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertThat(service.markRead(5L, 11L)).isTrue();
        ArgumentCaptor<Wrapper<MktNotice>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        WrapperAssert wa = WrapperAssert.of((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MktNotice>) cap.getValue());
        assertThat(wa.where()).contains("warehouse_id");
    }

    @Test void markReadOfForeignNoticeReturnsFalse() {
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThat(service.markRead(5L, 99L)).isFalse();
    }
}
