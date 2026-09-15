package com.zhyq.park.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.crm.entity.Channel;
import com.zhyq.park.crm.mapper.ChannelMapper;
import com.zhyq.park.importing.service.TabularFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 中介登记表导入。文件格式解析见 {@link TabularFileReader}(Excel/CSV/TXT/docx 表格)。
 *
 * <p>按表头文字认列:每个字段允许多个常见叫法,列顺序随意、多余列忽略。</p>
 *
 * <p>去重:同名称(+电话)已存在则跳过,重复导入同一份文件不会翻倍。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelImportService {

    /** 名称列的叫法,同时用来定位表头行 */
    private static final List<String> NAME_HEADERS = List.of(
            "中介名称", "中介公司", "中介公司名称", "公司名称", "机构名称", "渠道名称", "经纪人", "经纪人姓名", "名称");

    /** 字段 → 表头叫法(去空格后比对) */
    private static final Map<List<String>, BiConsumer<Channel, String>> COLUMN_MAP = new LinkedHashMap<>();

    static {
        COLUMN_MAP.put(NAME_HEADERS, Channel::setName);
        COLUMN_MAP.put(List.of("中介类型", "类型", "机构类型"), Channel::setAgencyType);
        COLUMN_MAP.put(List.of("联系人", "联系人姓名", "对接人"), Channel::setContact);
        COLUMN_MAP.put(List.of("联系电话", "电话", "手机", "手机号", "联系方式"), Channel::setPhone);
        COLUMN_MAP.put(List.of("微信", "微信号"), Channel::setWechat);
        COLUMN_MAP.put(List.of("所在地区", "地区", "区域", "覆盖区域"), Channel::setRegion);
        COLUMN_MAP.put(List.of("办公地址", "地址", "公司地址"), Channel::setAddress);
        COLUMN_MAP.put(List.of("擅长业务/资源", "擅长业务", "客户资源", "资源方向", "主营业务", "业务范围"), Channel::setResourceDesc);
        COLUMN_MAP.put(List.of("合作等级", "等级"), (c, v) -> c.setGrade(normalizeGrade(v)));
        COLUMN_MAP.put(List.of("对接负责人", "负责人", "园区负责人", "跟进负责人"), Channel::setOwnerName);
        COLUMN_MAP.put(List.of("佣金比例", "佣金比例(%)", "佣金比例%", "佣金点数"), (c, v) -> c.setCommissionRate(parseRate(v)));
        COLUMN_MAP.put(List.of("合作协议", "是否签协议", "是否签合作协议", "协议"), (c, v) -> c.setAgreementSigned(parseYes(v) ? 1 : 0));
        COLUMN_MAP.put(List.of("累计推荐客户", "推荐客户数", "累计推荐"), (c, v) -> c.setReferralCount(parseInt(v)));
        COLUMN_MAP.put(List.of("累计成交客户", "成交客户数", "累计成交"), (c, v) -> c.setDealCount(parseInt(v)));
        COLUMN_MAP.put(List.of("状态", "合作状态"), (c, v) -> c.setStatus(v.contains("暂停") || v.contains("停") ? 0 : 1));
        COLUMN_MAP.put(List.of("备注", "说明"), Channel::setRemark);
    }

    private final ChannelMapper channelMapper;
    private final ChannelFollowService channelFollowService;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(MultipartFile file) {
        TabularFileReader.Table table = TabularFileReader.read(file, NAME_HEADERS, "「中介名称」或「公司名称」");

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<List<String>> rows = table.rows();
        for (int r = table.headerRow() + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Channel channel = new Channel();
            for (Map.Entry<List<String>, BiConsumer<Channel, String>> e : COLUMN_MAP.entrySet()) {
                String v = table.value(row, e.getKey());
                if (!v.isBlank()) {
                    e.getValue().accept(channel, v);
                }
            }
            // 名称为空视为空行,静默跳过
            if (channel.getName() == null || channel.getName().isBlank()) {
                continue;
            }
            if (exists(channel.getName(), channel.getPhone())) {
                skipped++;
                continue;
            }
            try {
                if (channel.getAgencyType() == null) channel.setAgencyType("中介公司");
                if (channel.getStatus() == null) channel.setStatus(1);
                channel.setAgencyNo(channelFollowService.nextAgencyNo());
                channelMapper.insert(channel);
                imported++;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                log.warn("[crm] 中介导入第 {} 行编号冲突", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + channel.getName() + "」编号冲突,请重新导入");
            } catch (Exception ex) {
                log.warn("[crm] 中介导入第 {} 行失败", r + 1, ex);
                errors.add("第 " + (r + 1) + " 行:「" + channel.getName() + "」保存失败,请检查该行内容(如文字过长)");
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    private boolean exists(String name, String phone) {
        return channelMapper.selectCount(new LambdaQueryWrapper<Channel>()
                .eq(Channel::getName, name)
                .eq(phone != null && !phone.isBlank(), Channel::getPhone, phone)) > 0;
    }

    /** 「A」「A级」「核心」都归到 A-核心合作 这类标准值 */
    static String normalizeGrade(String v) {
        String s = v.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("A") || s.contains("核心")) return "A-核心合作";
        if (s.startsWith("B") || s.contains("一般")) return "B-一般合作";
        if (s.startsWith("C") || s.contains("潜在")) return "C-潜在合作";
        return v.trim();
    }

    /** 「2%」「2」都按 2% 处理;不猜 0.02 这类小数(0.5% 的佣金也真实存在),原样入库 */
    static BigDecimal parseRate(String v) {
        try {
            return new BigDecimal(v.replace("%", "").replace("％", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean parseYes(String v) {
        String s = v.trim();
        return s.startsWith("是") || s.startsWith("已") || s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("yes") || s.equals("1");
    }

    static Integer parseInt(String v) {
        try {
            return new BigDecimal(v.trim()).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
