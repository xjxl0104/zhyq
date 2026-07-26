package com.zhyq.park.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置:CORS 跨域 + OpenAPI 文档信息
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @org.springframework.beans.factory.annotation.Value("${zhyq.upload.path:./uploads}")
    private String uploadPath;

    @org.springframework.beans.factory.annotation.Value("${zhyq.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(
            org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        String location = "file:" + java.nio.file.Paths.get(uploadPath).toAbsolutePath() + "/";
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(location);
    }


    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("智慧园区管理系统 API")
                .description("zhyq-park 后端接口文档")
                .version("1.0.0"));
    }
}
