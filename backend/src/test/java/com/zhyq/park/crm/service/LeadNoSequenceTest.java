package com.zhyq.park.crm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 客户编号 / 跟进编号的序号解析。
 *
 * <p>编号按库中最大号 +1 递增而非随机 —— 登记表是人工按顺序看的，跳号会让人以为记录丢了。
 * 解析必须对脏数据宽容：老库补号、手工改过的编号都可能不合形制，此时应退回 0 让新号从
 * 0001 起，而不是抛异常把整个新增流程带崩。</p>
 */
class LeadNoSequenceTest {

    @Test
    void parsesLeadSequenceFromWellFormedNo() {
        assertEquals(1, LeadService.parseSeq("KH-0001"));
        assertEquals(7, LeadService.parseSeq("KH-0007"));
        assertEquals(1234, LeadService.parseSeq("KH-1234"));
        // 超过 4 位不截断:登记表用久了会破万
        assertEquals(12345, LeadService.parseSeq("KH-12345"));
    }

    @Test
    void leadSequenceFallsBackToZeroOnDirtyData() {
        assertEquals(0, LeadService.parseSeq(null));
        assertEquals(0, LeadService.parseSeq(""));
        assertEquals(0, LeadService.parseSeq("KH-"));
        assertEquals(0, LeadService.parseSeq("KH-abc"));
        assertEquals(0, LeadService.parseSeq("0001"));      // 缺前缀
        assertEquals(0, LeadService.parseSeq("GF-0001"));   // 拿错了跟进编号
    }

    @Test
    void parsesFollowSequence() {
        assertEquals(1, FollowService.parseSeq("GF-0001"));
        assertEquals(42, FollowService.parseSeq("GF-0042"));
        assertEquals(0, FollowService.parseSeq("KH-0001")); // 前缀不匹配
        assertEquals(0, FollowService.parseSeq(null));
    }
}
