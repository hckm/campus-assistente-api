package br.edu.usc.campusiachatbot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusIaChatbotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Campus IA Chatbot")
                        .description("API backend para atendimento administrativo e comercial de farmacia de manipulacao.")
                        .version("v1")
                        .contact(new Contact().name("USC"))
                        .license(new License().name("Uso academico")));
    }
}
