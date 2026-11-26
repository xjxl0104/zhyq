package com.zhyq.park;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef",
    "ZHYQ_AUTH_TRUSTED_PROXIES=127.0.0.1",
    "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef",
    "zhyq.auth.trusted-proxies=127.0.0.1"
})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
class ContextBootSmokeTest {
    @MockBean JdbcTemplate jdbcTemplate;
    @MockBean PlatformTransactionManager txManager;

    @TestConfiguration
    static class SqlCfg {
        @Bean DataSource dataSource() { return Mockito.mock(DataSource.class); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean b = new MybatisSqlSessionFactoryBean();
            b.setDataSource(ds);
            return b.getObject();
        }
    }

    @Test
    void contextLoads() {}
}
