package com.zhyq.park.marketing.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhyq.park.common.exception.BizException;
import com.zhyq.park.marketing.entity.MktWarehouse;
import com.zhyq.park.marketing.entity.MktWarehouseContact;
import com.zhyq.park.marketing.mapper.MktWarehouseMapper;
import com.zhyq.park.marketing.mapper.MktWarehouseContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every warehouse phone claim uses one transaction-held lock, including admin and password registration. */
@Service @RequiredArgsConstructor
public class WhPhoneClaimService {
    private final JdbcTemplate jdbc;
    private final MktWarehouseMapper warehouses;
    private final MktWarehouseContactMapper contacts;
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(String phone) {
        phone=LoginProfileRequirements.requirePhone(phone);
        jdbc.update("INSERT IGNORE INTO crm_warehouse_phone_lock(phone) VALUES (?)",phone);
        jdbc.queryForObject("SELECT phone FROM crm_warehouse_phone_lock WHERE phone=? FOR UPDATE",String.class,phone);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reserve(String phone, Long ownWarehouseId, Long ownContactId) {
        lock(phone);
        if (warehouses.selectCount(new LambdaQueryWrapper<MktWarehouse>().eq(MktWarehouse::getPhone,phone)
                .ne(ownWarehouseId!=null,MktWarehouse::getId,ownWarehouseId))>0
                || contacts.selectCount(new LambdaQueryWrapper<MktWarehouseContact>().eq(MktWarehouseContact::getPhone,phone)
                .ne(ownContactId!=null,MktWarehouseContact::getId,ownContactId))>0)
            throw new BizException(409,"该手机号已关联云仓账号，请核验后使用其他手机号");
    }
}
