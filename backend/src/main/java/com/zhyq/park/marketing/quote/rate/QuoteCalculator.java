package com.zhyq.park.marketing.quote.rate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Uses the same ticket and 30-day storage assumptions for all candidate warehouses. */
final class QuoteCalculator {
    private static final BigDecimal ZERO=BigDecimal.ZERO, ONE=BigDecimal.ONE, HUNDRED=BigDecimal.valueOf(100);
    private record Amount(BigDecimal low,BigDecimal high) {
        static Amount exact(BigDecimal n){return n==null?null:new Amount(n,n);}
        static Amount bounds(Collection<BigDecimal> values){return new Amount(Collections.min(values),Collections.max(values));}
        Amount times(BigDecimal n){return new Amount(low.multiply(n),high.multiply(n));}
        Amount plus(Amount other){return new Amount(low.add(other.low),high.add(other.high));}
        boolean exact(){return low.compareTo(high)==0;}
    }
    static List<Map<String,Object>> offers(QuoteCard card,JsonNode demand){
        return offers(card,demand,null);
    }
    static List<Map<String,Object>> offers(QuoteCard card,JsonNode demand,Integer dailyCapacity){
        var named=new LinkedHashSet<>(options(demand,"couriers")); named.remove("无");
        if(named.remove("其他")&&!demand.path("courierOther").asText("").isBlank())named.add(demand.path("courierOther").asText().trim());
        var byName=new LinkedHashMap<String,QuoteCard.Carrier>();
        card.carriers().forEach(c->byName.put(c.name(),c));
        if(named.isEmpty())named.addAll(byName.keySet());
        if(named.isEmpty())named.add("待匹配快递");
        return named.stream().map(name->offer(card,demand,name,byName.get(name),dailyCapacity)).toList();
    }
    private static Map<String,Object> offer(QuoteCard card,JsonNode d,String name,QuoteCard.Carrier carrier,Integer dailyCapacity){
        var missing=new ArrayList<String>();var notes=new ArrayList<String>();
        var fields=card.fields();var prices=new LinkedHashMap<String,Object>();
        Amount pack=packing(fields,d,missing),materials=materials(fields,d,missing);
        Amount freight=freight(carrier,d,missing),storage=storage(fields,d,missing);
        BigDecimal tax=taxFactor(fields,d.path("invoice").asText(""),missing);
        var services=new ArrayList<Map<String,Object>>();
        boolean extraQty=false;
        for(String service:options(d,"services")){
            var def=QuoteCard.SERVICES.stream().filter(s->s.key().equals(service)).findFirst();
            if(def.isEmpty()){missing.add("未配置增值服务："+service);extraQty=true;continue;}
            var rate=card.services().get(service);var line=new LinkedHashMap<String,Object>();
            line.put("label",service);line.put("unit",def.get().unit());line.put("rate",rate);services.add(line);
            if(rate==null){missing.add("增值服务未报价："+service);extraQty=true;}
            else if(rate.signum()>0){extraQty=true;missing.add("确认增值用量："+service+"（"+def.get().unit()+"）");}
        }
        putRange(prices,"handling",pack);putRange(prices,"materials",materials);putRange(prices,"freight",freight);
        // Only show a price when every component in that price's unit is known.
        Amount parcel=pack==null||materials==null||freight==null||tax==null?null:pack.plus(materials).plus(freight).times(tax);
        Amount billedStorage=storage==null||tax==null?null:storage.times(tax);
        var result=new LinkedHashMap<String,Object>();result.put("carrier",name);
        result.put("status",carrier==null?"missing":missing.isEmpty()?"estimate":"partial");
        putRange(result,"perParcel",parcel);putRange(result,"storageMonthly",billedStorage);
        result.put("storageMonthly",billedStorage!=null&&billedStorage.exact()?money(billedStorage.low):null);
        result.put("extrasMonthly",extraQty?null:ZERO);result.put("selectedServiceRates",services);
        result.put("unitPrices",prices);result.put("taxFactor",tax);result.put("missing",missing);
        result.put("dailyCapacity",dailyCapacity);
        noteCapacity(d,dailyCapacity,notes);
        notes.add("票价为仓内打包、标准包材和单一快递合计；仓租按30天另列，增值另列。");
        notes.add("运费按所选实重档的一般地区协议价估算；泡货、旺季、偏远附加和特约条款需按价卡核对。");
        if(!d.path("weightMix").asText("").isBlank()||options(d,"weightRanges").size()>1)
            notes.add("重量分布以区间展示；未按自由文字占比推算全国平均价。");
        result.put("notes",notes);result.put("scopeNote",String.join("；",java.util.stream.Stream.of(card.scopeNote(),carrier==null?"":carrier.scopeNote()).filter(x->!x.isBlank()).toList()));
        return result;
    }

    private static void noteCapacity(JsonNode demand,Integer dailyCapacity,List<String> notes){
        if(dailyCapacity==null){
            notes.add("云仓尚未登记日处理能力，接单前需核对产能。");
            return;
        }
        for(var field : Map.of("dailyOrders","日均单量","peakOrders","大促单量").entrySet()){
            String range=demand.path(field.getKey()).asText("");
            if(range.isEmpty())continue;
            var bounds=dailyBounds(range);
            var capacity=BigDecimal.valueOf(dailyCapacity);
            if(bounds[0].compareTo(capacity)>0){
                notes.add(field.getValue()+"下限已超过云仓登记的日处理能力（"+dailyCapacity+"单/天）。");
            }else if(bounds[1]==null||bounds[1].compareTo(capacity)>0){
                notes.add(field.getValue()+"可能超过云仓登记的日处理能力（"+dailyCapacity+"单/天），请核对实际单量与排班。");
            }
        }
    }
    private static Amount packing(Map<String,BigDecimal> f,JsonNode d,List<String> missing){
        BigDecimal batch=f.get("packBatch"),single=f.get("packSingle"),percent=decimal(d,"batchPercent");
        Amount pack;
        if(percent!=null&&percent.signum()==0)pack=Amount.exact(single);
        else if(percent!=null&&percent.compareTo(HUNDRED)==0)pack=Amount.exact(batch);
        else if(batch==null||single==null)pack=null;
        else if(percent==null)pack=Amount.bounds(List.of(batch,single));
        else pack=Amount.exact(batch.multiply(percent).add(single.multiply(HUNDRED.subtract(percent))).movePointLeft(2));
        if(pack==null){missing.add("缺少适用的批量或散单打包价");return null;}
        BigDecimal perParcel=decimal(d,"itemsPerParcel"),included=f.get("includedItems"),extra=f.get("extraItem");
        if(extra!=null&&extra.signum()==0)return pack;
        if(included==null||perParcel==null){missing.add("缺少每票商品件数或价卡包含件数");return null;}
        BigDecimal extraCount=perParcel.subtract(included);
        if(extraCount.signum()<=0)return pack;
        if(extra==null){missing.add("缺少续件价格");return null;}
        return pack.plus(Amount.exact(extraCount.multiply(extra)));
    }
    private static Amount materials(Map<String,BigDecimal> f,JsonNode d,List<String> missing){
        List<String> who=options(d,"materialProviders");
        if(who.equals(List.of("客户提供")))return Amount.exact(ZERO);
        if(who.isEmpty()){missing.add("需确认包材由谁提供");return null;}
        BigDecimal unit=f.get("material");
        if(unit==null){missing.add("缺少仓供标准包材单价");return null;}
        if(who.contains("客户提供")&&unit.signum()!=0){missing.add("双方供材，需确认仓供部分的实际成本");return null;}
        return Amount.exact(unit);
    }
    private static Amount freight(QuoteCard.Carrier carrier,JsonNode d,List<String> missing){
        if(carrier==null){missing.add("该仓未提供此快递价卡");return null;}
        List<String> weights=options(d,"weightRanges");
        if(weights.isEmpty()){missing.add("客户未勾选重量档");return null;}
        var prices=new ArrayList<BigDecimal>();
        for(String weight:weights){
            BigDecimal value=carrier.rates().get(weight);
            if(">3".equals(weight)){
                var kg=decimal(d,"heavyWeightKg");
                if(kg==null||kg.compareTo(BigDecimal.valueOf(3))<=0){missing.add("超过3kg需提供典型重量");continue;}
                value=carrier.heavyPrice(kg);
            }
            if(value==null)missing.add(carrier.name()+"未报价："+weight+"kg");
            else prices.add(value);
        }
        return prices.size()==weights.size()?Amount.bounds(prices):null;
    }
    private static Amount storage(Map<String,BigDecimal> f,JsonNode d,List<String> missing){
        BigDecimal pallets=decimal(d,"palletCount");
        if(pallets==null){missing.add("缺少预计在库板位数");return null;}
        if(pallets.signum()==0)return Amount.exact(ZERO);
        var bounds=dailyBounds(d.path("dailyOrders").asText(""));
        var threshold=f.get("highVolume");var prices=new ArrayList<BigDecimal>();boolean complete=true;
        if(threshold==null||bounds[0].compareTo(threshold)<0){
            var max=threshold==null?bounds[1]:threshold.subtract(ONE);
            if(bounds[1]!=null&&max!=null)max=max.min(bounds[1]);
            complete=storageTier(f.get("storageDaily"),f.get("freeTickets"),bounds[0],max,pallets,prices);
        }
        if(threshold!=null&&(bounds[1]==null||bounds[1].compareTo(threshold)>=0))
            complete=storageTier(f.get("highStorageDaily"),f.get("highFreeTickets"),bounds[0].max(threshold),bounds[1],pallets,prices)&&complete;
        if(!complete||prices.isEmpty()){missing.add("缺少适用仓租单价");return null;}
        return Amount.bounds(prices).times(pallets.multiply(BigDecimal.valueOf(30)));
    }
    private static boolean storageTier(BigDecimal price,BigDecimal free,BigDecimal low,BigDecimal high,BigDecimal pallets,List<BigDecimal> result){
        BigDecimal required=free==null?null:free.multiply(pallets);
        if(required!=null&&(high==null||high.compareTo(required)>=0))result.add(ZERO);
        if(required==null||low.compareTo(required)<0){
            if(price==null)return false;
            result.add(price);
        }
        return true;
    }
    private static BigDecimal[] dailyBounds(String range){
        if(range.matches(">=\\d{1,8}"))return new BigDecimal[]{new BigDecimal(range.substring(2)),null};
        if(range.matches("\\d{1,8}-\\d{1,8}")){var a=range.split("-");return new BigDecimal[]{new BigDecimal(a[0]),new BigDecimal(a[1]).subtract(ONE)};}
        return new BigDecimal[]{ZERO,null};
    }
    private static BigDecimal taxFactor(Map<String,BigDecimal> f,String invoice,List<String> missing){
        if("不开票".equals(invoice))return ONE;
        String key=switch(invoice){case "开普通发票"->"invoiceNormal";case "开增值税专票"->"invoiceSpecial";default->"";};
        if(key.isEmpty()){missing.add("开票要求待确认");return null;}
        var pct=f.get(key);if(pct==null){missing.add("所选票种的含税口径待核");return null;}
        return ONE.add(pct.movePointLeft(2));
    }
    private static void putRange(Map<String,Object> map,String key,Amount value){
        map.put(key+"Min",value==null?null:money(value.low));map.put(key+"Max",value==null?null:money(value.high));
    }
    private static BigDecimal money(BigDecimal n){return n.setScale(4,RoundingMode.HALF_UP);}
    private static BigDecimal decimal(JsonNode n,String key){JsonNode v=n.path(key);return v.isNumber()?v.decimalValue():null;}
    private static List<String> options(JsonNode n,String key){var values=new ArrayList<String>();n.path(key).forEach(v->values.add(v.asText()));return values;}
}
