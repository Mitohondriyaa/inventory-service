package io.github.mitohondriyaa.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    @Bean
    public OpenAPI inventoryOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Inventory Service API")
                .description("This is the REST API for Inventory Service")
                .version("1.0")
                .contact(new Contact()
                    .name("Nikita Dymko")
                    .email("mitohondriyaa@gmail.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT"))
            )
            .components(new Components()
                .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"))
            )
            .addSecurityItem(new SecurityRequirement()
                .addList("Bearer Authentication")
            );
    }
}