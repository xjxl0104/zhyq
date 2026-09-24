package com.zhyq.park.marketing.service;
import com.zhyq.park.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class MktDocumentAccessServiceTest {
    MktDocumentAccessService access=new MktDocumentAccessService(org.mockito.Mockito.mock(com.zhyq.park.file.mapper.FileBusinessAccessMapper.class));
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

    @Test void ordinaryDocumentsRequireTheirOwnBusinessReadPermission() {
        login("crm:marketing:promoter:query");
        for (String type : List.of("contract", "invoice", "finance_flow_receipt", "customer", "budget")) {
            assertThatThrownBy(() -> access.read(type)).isInstanceOf(BizException.class);
        }
        login("contract:query"); access.read("contract");
        assertThatThrownBy(() -> access.read("invoice")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> access.write("contract")).isInstanceOf(BizException.class);
    }
    @Test void pendingUploadsRemainPrivateEvenToAnotherOperatorWithSameBusinessPermission() {
        var file = new com.zhyq.park.file.entity.SysFile(); file.setId(12L); file.setBizType("contract"); file.setCreateBy("other");
        login("contract:query");
        assertThatThrownBy(() -> access.read(file)).isInstanceOf(BizException.class);
        file.setCreateBy("ops"); access.read(file);
        file.setBizId(7L); file.setCreateBy("other"); access.read(file);
    }
    @Test void privateScreenshotsAreOwnerOnlyAndUnknownTypesFailClosedEvenForAdmin() {
        var file = new com.zhyq.park.file.entity.SysFile(); file.setId(12L); file.setCreateBy("ops");
        login("irrelevant"); access.read(file); access.write(null);
        file.setCreateBy("other");
        assertThatThrownBy(() -> access.read(file)).isInstanceOf(BizException.class);
        login("ROLE_admin");
        assertThatThrownBy(() -> access.read("unregistered_type")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> access.write("unregistered_type")).isInstanceOf(BizException.class);
    }
    @Test void attachCannotClaimAnotherOperatorsUploadOrChangeItsBusinessType() {
        var file = new com.zhyq.park.file.entity.SysFile(); file.setId(12L); file.setBizType("contract"); file.setCreateBy("other");
        login("contract:edit");
        assertThatThrownBy(() -> access.attach(file, "contract")).isInstanceOf(BizException.class);
        file.setCreateBy("ops"); access.attach(file, "contract");
        assertThatThrownBy(() -> access.attach(file, "invoice")).isInstanceOf(BizException.class);
        file.setBizId(7L);
        assertThatThrownBy(() -> access.attach(file, "contract")).isInstanceOf(BizException.class);
    }
    private void login(String permission){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ops",null,List.of(new SimpleGrantedAuthority(permission))));}
}
