package com.example.loopa.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList());
    }

    private Info apiInfo() {
        return new Info()
                .title("Loopa API 명세서")
                .description("Loopa 프로젝트의 Swagger 문서입니다.")
                .version("1.0.0");
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local")
        );
    }
}
