package br.edu.usc.campusiachatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusIaChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusIaChatbotApplication.class, args);
    }
}
