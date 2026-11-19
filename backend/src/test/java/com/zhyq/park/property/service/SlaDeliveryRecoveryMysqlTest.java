package com.zhyq.park.property.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zhyq.park.common.config.MyMetaObjectHandler;
import com.zhyq.park.common.notify.InAppNotifyChannel;
import com.zhyq.park.common.notify.NotificationService;
import com.zhyq.park.property.mapper.WorkOrderLogMapper;
import com.zhyq.park.property.mapper.WorkOrderMapper;
import com.zhyq.park.system.mapper.MsgRecordMapper;
import com.zhyq.park.system.mapper.SysUserMapper;
import com.zhyq.park.todo.mapper.TodoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DIPARK_SLA_TEST_URL", matches = ".+")
class SlaDeliveryRecoveryMysqlTest {
    @TestConfiguration
    @EnableTransactionManagement
    static class Transactions {}

    final String prefix = UUID.randomUUID().toString().substring(0, 8);
    final long tenant = 1000L + Integer.toUnsignedLong(prefix.hashCode());
    final String receiver = "duty_" + prefix;
    JdbcTemplate db;

    long order(String name, int status, Integer oldSla) {
        String code = prefix + name;
        db.update("""
                INSERT INTO pm_work_order (tenant_id,code,title,status,assignee,sla_state,escalated,
                        create_time,update_time,sla_respond_min,sla_resolve_min)
                VALUES (?,?,?,?,?,?,?,DATE_SUB(NOW(),INTERVAL 2 DAY),'2024-01-19 09:12:00',1,1)
                """, tenant, code, code, status, receiver, oldSla, oldSla == null ? 0 : 1);
        long id = db.queryForObject("SELECT id FROM pm_work_order WHERE code=?", Long.class, code);
        if (oldSla != null) {
            db.update("""
                    INSERT INTO pm_work_order_log (tenant_id,order_id,action,operator,content,create_time)
                    VALUES (?,?,'SLA升级','system',?,'2024-01-19 09:12:00')
                    """, tenant, id, "旧版升级 " + code);
        }
        return id;
    }

    void check(long id, int confirmed, int todos, int messages, int newRecoveryLogs) {
        assertEquals(confirmed, db.queryForObject("""
                SELECT sla_delivery_confirmed_at IS NOT NULL FROM pm_work_order WHERE id=?
                """, Integer.class, id));
        assertEquals(todos, db.queryForObject("SELECT COUNT(*) FROM sys_todo WHERE biz_type='workorder' AND biz_id=?",
                Integer.class, id));
        assertEquals(messages, db.queryForObject("""
                SELECT COUNT(*) FROM sys_msg_record m JOIN pm_work_order w
                  ON m.content LIKE CONCAT('%工单 ',w.code,' %') AND m.tenant_id=w.tenant_id
                WHERE w.id=? AND m.receiver=?
                """, Integer.class, id, receiver));
        assertEquals(newRecoveryLogs, db.queryForObject("SELECT COUNT(*) FROM pm_work_order_log WHERE order_id=? AND action='SLA补送'",
                Integer.class, id));
    }

    @Test
    void recoversOnlyStillDueOrdersAndCommitsTheReceiptWithTheInbox() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("DIPARK_SLA_TEST_URL"),
                System.getenv().getOrDefault("DIPARK_SLA_TEST_USER", "root"),
                System.getenv().getOrDefault("DIPARK_SLA_TEST_PASSWORD", ""));
        db = new JdbcTemplate(source);
        assertTrue(db.queryForObject("SELECT DATABASE()", String.class).startsWith("dipark_sla_test_"),
                "Use a dedicated migrated scratch schema named dipark_sla_test_*");
        db.update("INSERT INTO sys_user (tenant_id,username,nickname,status) VALUES (?,?,?,1)", tenant, receiver, receiver);

        var config = new MybatisConfiguration();
        var global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig());
        global.setMetaObjectHandler(new MyMetaObjectHandler());
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        factory.setGlobalConfig(global);
        var sql = new SqlSessionTemplate(factory.getObject());
        for (var mapper : List.of(WorkOrderMapper.class, WorkOrderLogMapper.class, SysUserMapper.class,
                MsgRecordMapper.class, TodoMapper.class)) config.addMapper(mapper);
        var orders = sql.getMapper(WorkOrderMapper.class);
        var users = sql.getMapper(SysUserMapper.class);
        var interruptAfterInbox = new AtomicLong(-1);
        var channel = new InAppNotifyChannel(users, sql.getMapper(MsgRecordMapper.class), sql.getMapper(TodoMapper.class)) {
            @Override public boolean send(NotifyMessage message) {
                boolean sent = super.send(message);
                if (interruptAfterInbox.compareAndSet(message.bizId(), -1)) {
                    throw new IllegalStateException("simulated interruption after the inbox and todo inserts");
                }
                return sent;
            }
        };
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean("transactionManager", DataSourceTransactionManager.class,
                    () -> new DataSourceTransactionManager(source));
            context.registerBean(SlaEscalationWriter.class, () -> new SlaEscalationWriter(orders,
                    sql.getMapper(WorkOrderLogMapper.class), users, new NotificationService(List.of(channel))));
            context.refresh();
            var writer = context.getBean(SlaEscalationWriter.class);
            long active = order("active", 2, 1);
            long closed = order("closed", 6, 2);
            long advanced = order("advanced", 3, 1);
            long lateClose = order("lateClose", 4, 2);
            long changedThreshold = order("threshold", 3, 2);
            long previous = order("previous", 4, 2);
            long previousTodo = order("previousTodo", 4, 2);
            long fresh = order("fresh", 3, null);
            long retry = order("retry", 1, 1);
            db.update("UPDATE pm_work_order SET assignee='unbound' WHERE id=?", retry);
            db.update("UPDATE pm_work_order_log SET content=? WHERE order_id=?", "解决超时，已通知 " + receiver, previous);
            db.update("""
                    INSERT INTO sys_todo (tenant_id,title,biz_type,biz_id,owner,status,deleted)
                    SELECT tenant_id,CONCAT('工单 ',code,' 解决超时，请及时处理'),'workorder',id,?,3,1
                    FROM pm_work_order WHERE id=?
                    """, receiver, previousTodo);
            var staleClosed = orders.selectById(lateClose);
            var staleThreshold = orders.selectById(changedThreshold);
            db.update("UPDATE pm_work_order SET status=6 WHERE id=?", lateClose);
            db.update("UPDATE pm_work_order SET sla_resolve_min=999999 WHERE id=?", changedThreshold);
            writer.escalate(staleClosed, 2, "解决超时");
            writer.escalate(staleThreshold, 2, "解决超时");

            var sameOrder = orders.selectById(active);
            var start = new CyclicBarrier(2);
            var workers = Executors.newFixedThreadPool(2);
            try {
                Callable<Void> concurrentDelivery = () -> {
                    start.await(10, TimeUnit.SECONDS);
                    writer.escalate(sameOrder, 1, "响应超时");
                    return null;
                };
                var first = workers.submit(concurrentDelivery);
                var second = workers.submit(concurrentDelivery);
                first.get(15, TimeUnit.SECONDS);
                second.get(15, TimeUnit.SECONDS);
            } finally {
                workers.shutdownNow();
            }
            long interrupted = order("interrupted", 4, 2);
            interruptAfterInbox.set(interrupted);
            assertThrows(IllegalStateException.class,
                    () -> writer.escalate(orders.selectById(interrupted), 2, "解决超时"));
            check(interrupted, 0, 0, 0, 0);
            var scanner = new SlaEscalationJob(orders, writer);
            scanner.doScan();
            check(retry, 0, 0, 0, 0);
            db.update("UPDATE pm_work_order SET assignee=? WHERE id=?", receiver, retry);
            new SlaEscalationJob(orders, writer).doScan();
            scanner.doScan();

            check(active, 1, 1, 1, 1);
            check(interrupted, 1, 1, 1, 1);
            check(retry, 1, 1, 1, 1);
            for (long untouched : List.of(closed, advanced, lateClose, changedThreshold)) check(untouched, 0, 0, 0, 0);
            check(previous, 1, 0, 0, 0);
            check(previousTodo, 1, 1, 0, 0);
            check(fresh, 1, 1, 1, 0);
            assertEquals(1, db.queryForObject("""
                    SELECT COUNT(*) FROM pm_work_order_log WHERE order_id=? AND content=?
                      AND action='SLA升级' AND create_time='2024-01-19 09:12:00'
                    """, Integer.class, active, "旧版升级 " + prefix + "active"));
            assertEquals(1, db.queryForObject("""
                    SELECT COUNT(*) FROM pm_work_order WHERE id=? AND sla_state=1 AND escalated=1
                      AND update_time='2024-01-19 09:12:00'
                    """, Integer.class, active));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM pm_work_order_log WHERE order_id=? AND action='SLA升级'",
                    Integer.class, fresh));
        }
    }
}
