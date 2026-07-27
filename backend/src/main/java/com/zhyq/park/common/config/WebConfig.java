package com.zhyq.park.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置:OpenAPI 文档信息。
 * 附件不做静态目录映射(避免匿名可读),统一走鉴权下载接口 /file/download/{id}。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("智慧园区管理系统 API")
                .description("zhyq-park 后端接口文档")
                .version("1.0.0"));
    }
}
