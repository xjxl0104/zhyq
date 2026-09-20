package com.zhyq.park.marketing.engine;

/**
 * 收款链上的一个伙伴。
 *
 * @param promoterId   伙伴 id
 * @param positionCode 岗位 P1..P4
 * @param status       1正常 2冻结 3待审核 4已退出(退出者份额园区留存、不向上补)
 * @param internal     内部人员(手机号命中 sys_user):份额园区留存、不向上补
 */
public record ChainNode(Long promoterId, String positionCode, int status, boolean internal) {
}
