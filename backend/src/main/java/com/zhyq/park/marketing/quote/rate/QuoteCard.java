package com.zhyq.park.marketing.quote.rate;

import com.zhyq.park.common.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** Customer-facing nationwide contract rates. Omitted prices remain unknown; a free service is explicitly zero. */
public record QuoteCard(String scopeNote, Map<String,BigDecimal> fields, List<Carrier> carriers,
                        Map<String,BigDecimal> services, Map<String,Map<String,String>> tariff) {
    public record Carrier(String name, Map<String,BigDecimal> rates, BigDecimal heavyLabelFee,
                          BigDecimal heavyPerKg, String scopeNote,String heavyRule,BigDecimal heavyFirstKg,
                          BigDecimal heavyFirstFee,BigDecimal heavyStepKg,BigDecimal heavyStepFee,BigDecimal heavyMaxKg) {
        BigDecimal heavyPrice(BigDecimal kg){
            if(heavyMaxKg!=null&&kg.compareTo(heavyMaxKg)>0)return null;
            if("firstStep".equals(heavyRule)){
                if(heavyFirstKg==null)return null;
                BigDecimal steps=kg.subtract(heavyFirstKg).max(BigDecimal.ZERO).divide(heavyStepKg,0,java.math.RoundingMode.CEILING);
                return heavyFirstFee.add(steps.multiply(heavyStepFee));
            }
            return heavyLabelFee==null?null:heavyLabelFee.add(kg.setScale(0,java.math.RoundingMode.CEILING).multiply(heavyPerKg));
        }
    }
    public record Column(String key,String label,String unit) {}

    static final List<Column> FIELDS = List.of(
            new Column("packSingle","散单打包","元/票"),new Column("packBatch","同款批量打包","元/票"),
            new Column("includedItems","每票打包包含件数","件"),new Column("extraItem","超出含件数后","元/件"),
            new Column("material","仓供标准包材","元/票"),new Column("storageDaily","常规仓租","元/板位/天"),
            new Column("freeTickets","常规免租坪效（可留空）","票/板位/天"),
            new Column("highVolume","高单量仓租起点（可留空）","票/天"),
            new Column("highStorageDaily","高单量仓租","元/板位/天"),
            new Column("highFreeTickets","高单量免租坪效（可留空）","票/板位/天"),
            new Column("invoiceNormal","普通发票加价（已含税填0）","%"),new Column("invoiceSpecial","专票加价（已含税填0）","%"));
    static final List<Column> WEIGHTS = List.of(
            new Column("0-0.3","≤0.3 kg","元/票"),new Column("0.3-0.5","＞0.3至0.5 kg","元/票"),
            new Column("0.5-1","＞0.5至1 kg","元/票"),new Column("1-1.5","＞1至1.5 kg","元/票"),
            new Column("1.5-2","＞1.5至2 kg","元/票"),new Column("2-2.5","＞2至2.5 kg","元/票"),
            new Column("2.5-3","＞2.5至3 kg","元/票"));
    static final List<String> COURIERS=List.of("圆通","中通","顺丰","申通","韵达","极兔","京东","邮政");
    static final List<Column> SERVICES=List.of(
            svc("收货清点上架","箱"),svc("仓方装卸货","板"),svc("入库抽检","件"),svc("外观或效期全检","件"),
            svc("额外盘点","件"),svc("批次或残次品挑选","件"),svc("成品组套","套"),svc("称重组套","套"),
            svc("礼盒定制组装","套"),svc("拆套还原单品","套"),svc("制标贴大标","张"),svc("制标贴小标","张"),
            svc("增扫溯源码","单"),svc("折飞机盒","个"),svc("折异形纸盒","个"),svc("十字封箱","箱"),
            svc("单件防护","件"),svc("整箱防护","箱"),svc("整托防护","托"),svc("退件回仓上架","单"),
            svc("发货后拦截","单"),svc("残次品报废","吨"),svc("纸箱或周转箱回收","个"),svc("整箱调拨或退仓","箱"),
            svc("整板调拨或退仓","板"),svc("拼箱调拨或退仓","箱"),svc("随货带走卡板","板"));
    private static Column svc(String s,String unit){return new Column(s,s,"元/"+unit);}
    public static Map<String,Object> schema(){
        return Map.of("fields",FIELDS,"weights",WEIGHTS,"services",SERVICES,"couriers",COURIERS,"tariff",QuoteTariff.SECTIONS);
    }
    public static QuoteCard empty(){return new QuoteCard("",Map.of(),List.of(),Map.of(),Map.of());}

    static QuoteCard parse(JsonNode root) {
        keys(root,Set.of("scopeNote","fields","carriers","services","tariff"));
        String scope=text(root.path("scopeNote"),500);
        if(scope.isEmpty())throw bad("请注明报价适用范围、有效期及附加费用");
        var fields=numbers(root.path("fields"),FIELDS);
        for(String key:List.of("invoiceNormal","invoiceSpecial"))
            if(fields.containsKey(key)&&fields.get(key).compareTo(BigDecimal.valueOf(100))>0)throw bad("税率须在0至100之间");
        for(String key:List.of("includedItems","highVolume"))
            if(fields.containsKey(key)&&fields.get(key).stripTrailingZeros().scale()>0)throw bad("件数或票量须为整数");
        if(fields.containsKey("highVolume")&&fields.get("highVolume").signum()==0)throw bad("高单量起点须为正数");
        if((fields.containsKey("highFreeTickets")||fields.containsKey("highStorageDaily"))&&!fields.containsKey("highVolume"))
            throw bad("请填写高单量仓租的起始票数");
        var services=numbers(root.path("services"),SERVICES);
        var carriers=new ArrayList<Carrier>();
        Set<String> names=new HashSet<>();JsonNode raw=root.path("carriers");
        if(!raw.isArray()||raw.size()>32)throw bad("快递价卡须为列表，最多32家");
        for(var c:raw){
            keys(c,Set.of("name","scopeNote","rates","heavyLabelFee","heavyPerKg","heavyRule","heavyFirstKg","heavyFirstFee","heavyStepKg","heavyStepFee","heavyMaxKg"));
            String name=text(c.path("name"),32);
            if(name.isEmpty()||Set.of("无","其他").contains(name)||!names.add(name))throw bad("请填写不同的实际承运商名称");
            var rates=numbers(c.path("rates"),WEIGHTS);
            var label=number(c.path("heavyLabelFee"));var kg=number(c.path("heavyPerKg"));
            String rule=text(c.path("heavyRule"),16);
            if(rule.isEmpty())rule="labelKg";
            if(!Set.of("labelKg","firstStep").contains(rule))throw bad("不支持的重货计价方式");
            if("labelKg".equals(rule)&&(label==null)!=(kg==null))throw bad("超过3kg的面单费与公斤价需同时填写");
            var firstKg=number(c.path("heavyFirstKg"));var firstFee=number(c.path("heavyFirstFee"));
            var stepKg=number(c.path("heavyStepKg"));var stepFee=number(c.path("heavyStepFee"));
            var max=number(c.path("heavyMaxKg"));
            if("firstStep".equals(rule)&&(firstKg==null||firstKg.signum()<=0||firstFee==null||stepKg==null||stepKg.signum()<=0||stepFee==null))
                throw bad("请填写完整的首续重规则，首重、续重步长须大于0");
            if(max!=null&&(max.compareTo(BigDecimal.valueOf(3))<=0||("firstStep".equals(rule)&&max.compareTo(firstKg)<0)))
                throw bad("重货上限须大于3kg，且不得小于首重");
            carriers.add(new Carrier(name,rates,label,kg,text(c.path("scopeNote"),500),rule,firstKg,firstFee,stepKg,stepFee,max));
        }
        return new QuoteCard(scope,fields,List.copyOf(carriers),services,QuoteTariff.parse(root.path("tariff")));
    }
    private static Map<String,BigDecimal> numbers(JsonNode obj,List<Column> allowed){
        if(obj.isMissingNode()||obj.isNull())return Map.of();
        keys(obj,new HashSet<>(allowed.stream().map(Column::key).toList()));
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        for(Column c:allowed){var n=number(obj.path(c.key()));if(n!=null)result.put(c.key(),n);}
        return result;
    }
    static BigDecimal number(JsonNode v){
        if(v.isMissingNode()||v.isNull()||v.isTextual()&&v.asText().isBlank())return null;
        String raw=v.asText().trim();if(!(v.isTextual()||v.isNumber())||!raw.matches("[0-9]{1,8}(\\.[0-9]{1,4})?"))throw bad("金额或数量须为非负数，最多四位小数");
        return new BigDecimal(raw);
    }
    private static String text(JsonNode v,int max){
        if(v.isMissingNode()||v.isNull())return "";
        if(!v.isTextual()||v.textValue().length()>max)throw bad("文本格式错误或过长");
        return v.textValue().trim();
    }
    private static void keys(JsonNode node,Set<String> allowed){
        if(node==null||!node.isObject())throw bad("价卡格式错误");
        node.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw bad("不支持的价卡字段");});
    }
    static BizException bad(String msg){return new BizException(400,msg);}
}
