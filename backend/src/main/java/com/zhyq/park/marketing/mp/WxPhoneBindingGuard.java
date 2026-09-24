package com.zhyq.park.marketing.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** A self-reported registration phone cannot silently merge a password account into a WeChat identity. */
@Service
@RequiredArgsConstructor
public class WxPhoneBindingGuard {
    private final MktCredentialMapper credentialMapper;

    public void assertCanBind(String identityType, Long identityId) {
        MktCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<MktCredential>()
                .eq(MktCredential::getIdentityType, identityType).eq(MktCredential::getIdentityId, identityId).last("limit 1"));
        if (credential != null && StringUtils.hasText(credential.getRegistrationPhone()))
            throw new BizException("该手机号存在待核验的账号资料,请使用账号密码登录或联系园区运营核验后绑定微信");
    }
}
