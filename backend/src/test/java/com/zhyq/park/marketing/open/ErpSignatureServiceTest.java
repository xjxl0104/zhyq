package com.zhyq.park.marketing.open;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhyq.park.marketing.entity.MktWarehouseErp;
import com.zhyq.park.marketing.mapper.MktWarehouseErpMapper;
import com.zhyq.park.receivable.service.FieldEncryptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 开放接口验签(PARK-MKT-001 §7 测试要求:错签 / 过期 / 重放)。 */
@ExtendWith(MockitoExtension.class)
class ErpSignatureServiceTest {

    static final String SECRET = "s3cr3t-for-test";
    static final String BODY = "{\"event\":\"order.shipped\",\"order_no\":\"OUT1\"}";

    @Mock MktWarehouseErpMapper erpMapper;
    @Mock FieldEncryptionService encryption;
    @Mock JdbcTemplate jdbc;
    ErpSignatureService service;

    @BeforeAll
    static void initMp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MktWarehouseErp.class);
    }

    @BeforeEach
    void setUp() {
        service = new ErpSignatureService(erpMapper, encryption, jdbc);
        MktWarehouseErp cred = new MktWarehouseErp();
        cred.setId(1L); cred.setAppId("wh_test"); cred.setSecretEnc("enc"); cred.setStatus(1); cred.setWarehouseId(7L);
        lenient().when(erpMapper.selectOne(any(Wrapper.class))).thenReturn(cred);
        lenient().when(encryption.decrypt("enc")).thenReturn(SECRET);
        lenient().when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void validSignaturePasses() {
        String ts = now();
        String sig = ErpSignatureService.sign(SECRET, ts, "n1", BODY);
        assertThat(service.verify("wh_test", ts, "n1", sig, BODY)).isInstanceOf(ErpSignatureService.Ok.class);
    }

    @Test
    void signatureIsCaseInsensitiveHex() {
        String ts = now();
        String sig = ErpSignatureService.sign(SECRET, ts, "n1", BODY).toUpperCase();
        assertThat(service.verify("wh_test", ts, "n1", sig, BODY)).isInstanceOf(ErpSignatureService.Ok.class);
    }

    @Test
    void wrongSecretIsRejected() {
        String ts = now();
        String sig = ErpSignatureService.sign("other", ts, "n1", BODY);
        ErpSignatureService.VerifyResult r = service.verify("wh_test", ts, "n1", sig, BODY);
        assertThat(r).isInstanceOf(ErpSignatureService.Fail.class);
        assertThat(((ErpSignatureService.Fail) r).code()).isEqualTo("SIGN_INVALID");
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void tamperedBodyIsRejected() {
        String ts = now();
        String sig = ErpSignatureService.sign(SECRET, ts, "n1", BODY);
        ErpSignatureService.VerifyResult r = service.verify("wh_test", ts, "n1", sig, BODY.replace("OUT1", "OUT2"));
        assertThat(((ErpSignatureService.Fail) r).code()).isEqualTo("SIGN_INVALID");
    }

    @Test
    void expiredTimestampIsRejected() {
        String ts = String.valueOf(Instant.now().getEpochSecond() - 301);
        String sig = ErpSignatureService.sign(SECRET, ts, "n1", BODY);
        ErpSignatureService.VerifyResult r = service.verify("wh_test", ts, "n1", sig, BODY);
        assertThat(((ErpSignatureService.Fail) r).code()).isEqualTo("SIGN_INVALID");
        assertThat(((ErpSignatureService.Fail) r).message()).contains("时间戳");
    }

    @Test
    void replayedNonceIsRejected() {
        when(jdbc.update(anyString(), eq("wh_test"), eq("n1"), any())).thenThrow(new DuplicateKeyException("pk"));
        String ts = now();
        String sig = ErpSignatureService.sign(SECRET, ts, "n1", BODY);
        ErpSignatureService.VerifyResult r = service.verify("wh_test", ts, "n1", sig, BODY);
        assertThat(((ErpSignatureService.Fail) r).code()).isEqualTo("NONCE_REPLAY");
    }

    @Test
    void disabledCredentialIsRejected() {
        MktWarehouseErp off = new MktWarehouseErp(); off.setAppId("wh_test"); off.setSecretEnc("enc"); off.setStatus(0);
        when(erpMapper.selectOne(any(Wrapper.class))).thenReturn(off);
        String ts = now();
        ErpSignatureService.VerifyResult r = service.verify("wh_test", ts, "n1", ErpSignatureService.sign(SECRET, ts, "n1", BODY), BODY);
        assertThat(((ErpSignatureService.Fail) r).code()).isEqualTo("APP_DISABLED");
    }

    @Test
    void unknownAppIdAndMissingHeadersAreRejected() {
        when(erpMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(((ErpSignatureService.Fail) service.verify("wh_x", now(), "n", "s", BODY)).code()).isEqualTo("SIGN_INVALID");
        assertThat(((ErpSignatureService.Fail) service.verify(null, now(), "n", "s", BODY)).code()).isEqualTo("SIGN_INVALID");
    }

    private static String now() {
        return String.valueOf(Instant.now().getEpochSecond());
    }
}
