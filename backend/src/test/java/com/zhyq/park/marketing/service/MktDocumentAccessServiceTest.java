package com.zhyq.park.marketing.service;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class MktDocumentAccessServiceTest {
    MktDocumentAccessService access=new MktDocumentAccessService();
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void unrelatedAdminPermissionCannotReadPayoutEvidence(){
        login("crm:marketing:promoter:query");
        assertThatThrownBy(()->access.read("mkt_withdrawal")).isInstanceOf(BizException.class);
    }
    @Test void auditorCanReadButCannotUploadPayoutEvidence(){
        login("crm:marketing:withdrawal:audit");access.read("mkt_withdrawal");
        assertThatThrownBy(()->access.write("mkt_withdrawal")).isInstanceOf(BizException.class);
    }
    @Test void financeCanUploadPayoutEvidence(){login("crm:marketing:withdrawal:pay");access.write("mkt_withdrawal");}
    @Test void directPaymentEvidenceUsesTheSamePermissionsAsReceiptRegistration(){
        login("crm:marketing:bill:pay");access.write("mkt_direct_payment");
        login("crm:marketing:bill:query");access.read("mkt_direct_payment");
        assertThatThrownBy(()->access.write("mkt_direct_payment")).isInstanceOf(BizException.class);
        login("crm:marketing:settlement:pay");
        assertThatThrownBy(()->access.write("mkt_direct_payment")).isInstanceOf(BizException.class);
    }
    private void login(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ops",null,List.of(new SimpleGrantedAuthority(permission))));}
}
