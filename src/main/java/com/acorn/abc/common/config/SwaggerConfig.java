package com.acorn.abc.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ABC API")
                        .description("Acorn Book Cloud API 문서\n\n" +
                                "**인증이 필요한 API 사용법**\n" +
                                "1. `POST /api/v1/auth/login` 으로 로그인\n" +
                                "2. 응답의 `accessToken` 복사\n" +
                                "3. 우측 상단 **Authorize** 버튼 클릭\n" +
                                "4. `Bearer {accessToken}` 형식으로 입력 후 Authorize")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
