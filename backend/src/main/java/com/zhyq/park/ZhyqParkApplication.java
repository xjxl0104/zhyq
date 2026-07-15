package com.zhyq.park;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智慧园区管理系统启动类
 */
@SpringBootApplication
@MapperScan("com.zhyq.park.**.mapper")
@EnableScheduling
public class ZhyqParkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhyqParkApplication.class, args);
        System.out.println("""

                ========================================================
                  智慧园区管理系统 zhyq-park 启动成功
                  接口文档: http://localhost:8090/api/doc.html
                ========================================================
                """);
    }
}
