package com.zhyq.park.marketing.password;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhyq.park.auth.JwtService;
import com.zhyq.park.auth.JwtAccountService;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.common.setting.BizSettings;
import com.zhyq.park.marketing.entity.MktCredential;
import com.zhyq.park.marketing.entity.MktPromoter;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktCredentialMapper;
import com.zhyq.park.marketing.mapper.MktPromoterMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import com.zhyq.park.marketing.service.MktAuditService;
import com.zhyq.park.marketing.service.MktPromoterService;
import com.zhyq.park.marketing.service.MktWarehouseOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordAuthService {
    public enum Identity {
        MP("mp", "ROLE_MP"), WH("wh", "ROLE_WH");
        final String code;
        final String role;
        Identity(String code, String role) { this.code = code; this.role = role; }
    }

    public record LoginRequest(String username, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
        @Override public String toString() { return "LoginRequest[credentials=REDACTED]"; }
    }
    public record RegisterRequest(String username, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password, String phone, String name,
                                  String inviteCode, String warehouseName, Boolean agreed) {
        @Override public String toString() { return "RegisterRequest[credentials=REDACTED]"; }
    }
    public record SetupRequest(String username, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
                               @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword) {
        @Override public String toString() { return "SetupRequest[credentials=REDACTED]"; }
    }

    private final MktCredentialMapper credentials;
    private final MktPromoterMapper promoters;
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseContactMapper contacts;
    private final MktPromoterService promoterService;
    private final MktWarehouseOnboardingService warehouseService;
    private final MktAuditService auditService;
    private final BizSettings settings;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PasswordAttemptLimiter limiter;
    // A valid BCrypt hash makes unknown-user attempts perform the same expensive verification.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    public Map<String, Object> login(Identity identity, LoginRequest request, String remoteAddress) {
        limiter.check("login:ip:" + remoteAddress, 60);
        String username = username(request.username());
        checkPasswordLength(request.password());
        String attemptKey = "login:" + identity.code + ":" + username;
        limiter.check(attemptKey, 10);
        MktCredential credential = findByUsername(identity, username);
        boolean matches = encoder.matches(request.password(), credential == null ? DUMMY_HASH : credential.getPasswordHash());
        if (credential == null || !matches || !Integer.valueOf(1).equals(credential.getStatus())) {
            throw new BizException(401, "账号或密码错误");
        }
        Map<String, Object> result = authenticated(identity, credential.getIdentityId());
        limiter.clear(attemptKey);
        return result;
    }

    @Transactional
    public Map<String, Object> register(Identity identity, RegisterRequest request, String remoteAddress) {
        limiter.check("register:ip:" + remoteAddress, 10);
        String username = username(request.username());
        validateNewPassword(request.password());
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!phone.matches("^1\\d{10}$")) throw new BizException(400, "手机号格式不正确");
        String name = requiredText(request.name(), "姓名", 32);
        if (!Boolean.TRUE.equals(request.agreed())) throw new BizException(400, "请先同意用户协议和隐私政策");
        if (findByUsername(identity, username) != null) throw new BizException(409, "该账号已注册，请直接登录");
        rejectExistingPhone(phone);
        // Hash before creating business records; the surrounding transaction also rolls back conflicts.
        String hash = encoder.encode(request.password());
        Long identityId;
        if (identity == Identity.MP) {
            String invite = request.inviteCode() == null ? null : request.inviteCode().trim().toUpperCase(Locale.ROOT);
            if (StringUtils.hasText(invite) && !invite.matches("^[A-Z2-9]{8}$")) throw new BizException(400, "邀请码格式不正确");
            MktPromoter promoter = new MktPromoter();
            promoter.setName(name);
            promoter.setPhone(phone);
            promoter.setLastLogin(LocalDateTime.now());
            promoter.setAgreementVersion("v1");
            promoter.setAgreedAt(LocalDateTime.now());
            promoter.setIdVerified(0);
            promoter.setRemark("账号密码自助注册；联系电话未经手机验证");
            if (!StringUtils.hasText(invite)) {
                promoter.setInviteDeadline(LocalDateTime.now().plusDays(settings.getInt("marketing", "invite_grace_days", 7)));
            }
            promoterService.register(promoter, invite, "mp");
            identityId = promoter.getId();
        } else {
            MktWarehouse warehouse = new MktWarehouse();
            warehouse.setCode("WH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
            warehouse.setName(requiredText(request.warehouseName(), "云仓名称", 100));
            warehouse.setContact(name);
            warehouse.setPhone(phone);
            warehouse.setRemark("账号密码自助申请；联系电话未经手机验证，待运营资质审核");
            warehouseService.apply(warehouse);
            identityId = warehouse.getId();
        }
        MktCredential credential = new MktCredential();
        credential.setIdentityType(identity.code);
        credential.setIdentityId(identityId);
        credential.setUsername(username);
        credential.setPasswordHash(hash);
        credential.setRegistrationPhone(phone);
        credential.setAgreedAt(LocalDateTime.now());
        credential.setStatus(1);
        insertCredential(credential);
        auditService.log("password.register", identity == Identity.MP ? "promoter" : "warehouse", identityId,
                "自助账号注册（联系电话未经验证）");
        return authenticated(identity, identityId);
    }

    public Map<String, Object> status(Identity identity) {
        Long identityId = currentIdentityId(identity);
        requireActiveIdentity(identity, identityId);
        MktCredential credential = findByIdentity(identity, identityId);
        return Map.of("configured", credential != null, "username", credential == null ? "" : credential.getUsername());
    }

    @Transactional
    public void setup(Identity identity, SetupRequest request, String remoteAddress) {
        Long identityId = currentIdentityId(identity);
        if (identity == Identity.MP) JwtAccountService.assertPromoterActive(promoters.selectForUpdate(identityId));
        else requireActiveIdentity(identity, identityId);
        limiter.check("setup:ip:" + remoteAddress, 30);
        String attemptKey = "setup:" + identity.code + ":" + identityId;
        limiter.check(attemptKey, 10);
        String username = username(request.username());
        validateNewPassword(request.password());
        MktCredential credential = findByIdentity(identity, identityId);
        if (credential != null) {
            if (!Integer.valueOf(1).equals(credential.getStatus())) throw new BizException(403, "账号已停用，请联系园区运营");
            if (!StringUtils.hasText(request.currentPassword())) throw new BizException(400, "请填写当前密码");
            checkPasswordLength(request.currentPassword());
            if (!encoder.matches(request.currentPassword(), credential.getPasswordHash())) {
                throw new BizException(400, "当前密码不正确");
            }
        }
        MktCredential occupied = findByUsername(identity, username);
        if (occupied != null && !identityId.equals(occupied.getIdentityId())) throw new BizException(409, "该账号已被使用");
        try {
            if (credential == null) {
                credential = new MktCredential();
                credential.setIdentityType(identity.code);
                credential.setIdentityId(identityId);
                credential.setUsername(username);
                credential.setPasswordHash(encoder.encode(request.password()));
                credential.setStatus(1);
                insertCredential(credential);
            } else {
                String oldHash = credential.getPasswordHash();
                int updated = credentials.update(null, new LambdaUpdateWrapper<MktCredential>()
                        .eq(MktCredential::getId, credential.getId()).eq(MktCredential::getPasswordHash, oldHash)
                        .set(MktCredential::getUsername, username).set(MktCredential::getPasswordHash, encoder.encode(request.password())));
                if (updated != 1) throw new BizException(409, "账号已更新，请重新登录后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "账号已被使用或已设置，请刷新后重试");
        }
        auditService.log("password.setup", identity == Identity.MP ? "promoter" : "warehouse", identityId, "设置账号密码");
        limiter.clear(attemptKey);
    }

    private void insertCredential(MktCredential credential) {
        try { credentials.insert(credential); }
        catch (DuplicateKeyException e) { throw new BizException(409, "账号或手机号已注册，请登录已有账号"); }
    }

    private void rejectExistingPhone(String phone) {
        boolean exists = promoters.selectCount(new LambdaQueryWrapper<MktPromoter>().eq(MktPromoter::getPhone, phone)) > 0
                || warehouses.selectCount(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone, phone)) > 0
                || contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getPhone, phone)) > 0;
        if (exists) throw new BizException(409, "该手机号已有业务档案，请通过原微信登录后设置账号密码，或联系园区运营；不能凭填写手机号合并身份");
    }

    private Map<String, Object> authenticated(Identity identity, Long identityId) {
        Object business = requireActiveIdentity(identity, identityId);
        Map<String, Object> result = new HashMap<>();
        result.put("registered", true);
        result.put("token", jwt.issueForIdentity(identity.code, identityId));
        result.put("openid", null);
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", identityId);
        if (business instanceof MktPromoter promoter) {
            promoters.update(null, new LambdaUpdateWrapper<MktPromoter>().eq(MktPromoter::getId, identityId)
                    .set(MktPromoter::getLastLogin, LocalDateTime.now()));
            profile.put("name", promoter.getName());
            profile.put("status", promoter.getStatus());
            profile.put("inviteCode", promoter.getInviteCode());
            result.put("me", profile);
        } else if (business instanceof MktWarehouse warehouse) {
            profile.put("name", warehouse.getName());
            profile.put("code", warehouse.getCode());
            profile.put("joinStatus", warehouse.getJoinStatus());
            result.put("warehouse", profile);
            result.put("warehouseId", identityId);
        }
        return result;
    }

    private Object requireActiveIdentity(Identity identity, Long identityId) {
        if (identity == Identity.MP) {
            MktPromoter promoter = promoters.selectById(identityId);
            JwtAccountService.assertPromoterActive(promoter);
            return promoter;
        }
        MktWarehouse warehouse = warehouses.selectById(identityId);
        JwtAccountService.assertWarehouseActive(warehouse);
        return warehouse;
    }

    private MktCredential findByUsername(Identity identity, String username) {
        return credentials.selectOne(new LambdaQueryWrapper<MktCredential>()
                .eq(MktCredential::getIdentityType, identity.code).eq(MktCredential::getUsername, username));
    }

    private MktCredential findByIdentity(Identity identity, Long identityId) {
        return credentials.selectOne(new LambdaQueryWrapper<MktCredential>()
                .eq(MktCredential::getIdentityType, identity.code).eq(MktCredential::getIdentityId, identityId));
    }

    private Long currentIdentityId(Identity identity) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || !authentication.getName().startsWith(identity.code + ":")
                || authentication.getAuthorities().stream().noneMatch(a -> identity.role.equals(a.getAuthority()))) {
            throw new BizException(401, "请先登录对应的小程序身份");
        }
        try {
            long id = Long.parseLong(authentication.getName().substring(3));
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) { throw new BizException(401, "登录身份无效，请重新登录"); }
    }

    private static String username(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9_]{4,32}$")) throw new BizException(400, "账号须为 4–32 位字母、数字或下划线");
        return normalized;
    }

    private static void validateNewPassword(String password) {
        checkPasswordLength(password);
        if (!password.matches("(?s).*[A-Za-z].*") || !password.matches("(?s).*\\d.*")) {
            throw new BizException(400, "密码须同时包含字母和数字");
        }
    }

    private static void checkPasswordLength(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException(400, "密码须为 8–64 位，且 UTF-8 编码不超过 72 字节");
        }
    }

    private static String requiredText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) throw new BizException(400, label + "必填且不能超过 " + maxLength + " 字");
        return normalized;
    }
}
