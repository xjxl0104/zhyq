package com.zhyq.park.marketing.quote;

import com.zhyq.park.common.exception.BizException;
import java.math.BigDecimal;
import java.util.*;

/** Canonical customer-facing demand. Monetary rates belong to the warehouse rate card. */
public final class QuoteRequirements {
    private QuoteRequirements() {}

    /** Only standard options and numeric workload may be compared by the external AI.
     * Never include other/custom names, remarks, contact details or free-text quantities. */
    public static Map<String,Object> assessmentProfile(com.fasterxml.jackson.databind.JsonNode demand) {
        var profile=new TreeMap<String,Object>();
        for (var field:COLLECTIONS.entrySet()) {
            var selected=new ArrayList<String>();
            for(var value:demand.path(field.getKey()))
                if (value.isTextual() && field.getValue().contains(value.textValue())) selected.add(value.textValue());
            if(!selected.isEmpty()) profile.put(field.getKey(),selected);
        }
        for(var field:CHOICES.entrySet()) {
            String choice=demand.path(field.getKey()).asText("");
            if(field.getValue().contains(choice)) profile.put(field.getKey(),choice);
        }
        for(String key:List.of("palletCount","heavyWeightKg","itemsPerParcel","batchPercent")) {
            if(demand.path(key).isNumber()) profile.put(key,demand.path(key).decimalValue());
        }
        return profile;
    }
    static final List<String> SERVICES = List.of("收货清点上架","仓方装卸货","入库抽检","外观或效期全检","额外盘点","批次或残次品挑选",
            "成品组套","称重组套","礼盒定制组装","拆套还原单品","制标贴大标","制标贴小标","增扫溯源码","折飞机盒","折异形纸盒","十字封箱","单件防护","整箱防护","整托防护",
            "退件回仓上架","发货后拦截","残次品报废","纸箱或周转箱回收","整箱调拨或退仓","整板调拨或退仓","拼箱调拨或退仓","随货带走卡板");
    private static final Map<String,List<String>> COLLECTIONS = Map.of(
        "salesPlatforms",List.of("天猫","京东","拼多多","有赞","抖音","视频号","其他"),
        "productCategories",List.of("食品饮料","美妆护肤","日化清洁","服装鞋帽","家居家纺","母婴用品","宠物用品","数码家电","文体办公","箱包配饰","五金汽配","其他"),
        "couriers",List.of("圆通","中通","顺丰","申通","韵达","极兔","京东","邮政","其他","无"),
        "weightRanges",List.of("0-0.3","0.3-0.5","0.5-1","1-1.5","1.5-2","2-2.5","2.5-3",">3"),
        "materialProviders",List.of("客户提供","云仓提供"),"erpSystems",List.of("旺店通","聚水潭","管家婆","管易云","万里牛","其他"),
        "services",SERVICES);
    private static final Map<String,List<String>> CHOICES = Map.of(
        "skuCount",List.of("0-10","10-20","20-50","50-100","100-500",">=500"),
        "dailyOrders",List.of("0-200","200-1000","1000-3000","3000-5000","5000-10000",">=10000"),
        "peakOrders",List.of("0-200","200-1000","1000-3000","3000-5000","5000-10000","10000-30000","30000-50000",">=50000"),
        "invoice",List.of("不开票","开普通发票","开增值税专票","待确认"));
    private static final Map<String,String> OTHER = Map.of("salesPlatformOther","salesPlatforms","productCategoryOther","productCategories",
            "courierOther","couriers","erpOther","erpSystems");

    public static Map<String,Object> normalize(Object raw) {
        if (!(raw instanceof Map<?,?> fields)) throw bad("请填写报价需求");
        Map<String,Object> result = new TreeMap<>();
        for (var entry : COLLECTIONS.entrySet()) {
            Object value = fields.get(entry.getKey());
            if (value == null) continue;
            if (!(value instanceof List<?> list) || list.size() > entry.getValue().size()
                    || list.stream().anyMatch(v -> !(v instanceof String) || !entry.getValue().contains(v))
                    || new HashSet<>(list).size() != list.size()) throw bad("报价选项无效或重复");
            if (!list.isEmpty()) result.put(entry.getKey(), entry.getValue().stream().filter(list::contains).toList());
        }
        if (selected(result,"couriers","无") && ((List<?>)result.get("couriers")).size() > 1) throw bad("未指定快递时，请只选“无”");
        for (var entry : OTHER.entrySet()) {
            if (!selected(result,entry.getValue(),"其他")) continue;
            String value = text(fields.get(entry.getKey()),100);
            if (value == null) throw bad("请补充“其他”选项名称");
            result.put(entry.getKey(),value);
        }
        for (var entry : CHOICES.entrySet()) {
            String value = text(fields.get(entry.getKey()),40);
            if (value == null) continue;
            if (!entry.getValue().contains(value)) throw bad("请选择有效的报价区间或开票要求");
            result.put(entry.getKey(),value);
        }
        number(fields,result,"palletCount",0,10000000,2);
        if (selected(result,"weightRanges",">3")) {
            BigDecimal heavy = number(fields,result,"heavyWeightKg",3,1000000,3);
            if (heavy != null && heavy.compareTo(BigDecimal.valueOf(3)) <= 0) throw bad("超重包裹重量应大于3公斤");
        }
        number(fields,result,"itemsPerParcel",1,100000,0);
        number(fields,result,"batchPercent",0,100,2);
        for (String key : List.of("weightMix","packingNote")) {
            String value = text(fields.get(key),500);
            if (value != null) result.put(key,value);
        }
        if (fields.get("serviceQuantities") != null) {
            if (!(fields.get("serviceQuantities") instanceof Map<?,?> rows)) throw bad("增值服务用量格式不正确");
            Map<String,String> quantities=new TreeMap<>();
            for (String key : List.of("inbound","packing","returns")) {
                String value = text(rows.get(key),300);
                if (value != null) quantities.put(key,value);
            }
            if (!quantities.isEmpty()) {
                if (!result.containsKey("services")) throw bad("请先勾选对应的增值服务");
                result.put("serviceQuantities",quantities);
            }
        }
        if (result.isEmpty()) throw bad("请至少填写一项报价需求");
        return result;
    }
    private static boolean selected(Map<String,Object> rows,String key,String option) {
        return rows.get(key) instanceof List<?> list && list.contains(option);
    }
    private static String text(Object raw,int limit) {
        if (raw == null) return null;
        if (!(raw instanceof String value) || value.length()>limit) throw bad("报价文字格式或长度不正确");
        return value.isBlank()?null:value.strip();
    }
    private static BigDecimal number(Map<?,?> src,Map<String,Object> result,String key,int min,int max,int scale) {
        Object value=src.get(key);
        if (value == null || value instanceof String s && s.isBlank()) return null;
        String literal=value.toString().trim();
        if (!(value instanceof String || value instanceof Number) || !literal.matches("[0-9]{1,9}(?:\\.[0-9]{1,6})?"))
            throw bad("数量、重量或占比须为有效数字");
        BigDecimal n=new BigDecimal(literal).stripTrailingZeros();
        if (n.scale()>scale || n.compareTo(BigDecimal.valueOf(min))<0 || n.compareTo(BigDecimal.valueOf(max))>0)
            throw bad("数量、重量或占比超出有效范围");
        if (n.scale()<0) n=n.setScale(0);
        result.put(key,n);
        return n;
    }
    private static BizException bad(String message) {return new BizException(400,message);}
}
