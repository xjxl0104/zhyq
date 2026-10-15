package com.zhyq.park.marketing.yunshan;

import com.zhyq.park.common.exception.BizException;
import lombok.Getter;

/** 云山返回非 00000,或网络/报文异常。apiCode 为云山 code(如 BIZ_SCOPE_FORBIDDEN)或本地 NETWORK / BAD_RESPONSE。 */
@Getter
public class YunshanApiException extends BizException {

    private final String apiCode;

    public YunshanApiException(String apiCode, String message) {
        super("云山接口:" + message + " [" + apiCode + "]");
        this.apiCode = apiCode;
    }
}
