package com.quantstream.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QuantStreamBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantStreamBackendApplication.class, args);
    }
}
