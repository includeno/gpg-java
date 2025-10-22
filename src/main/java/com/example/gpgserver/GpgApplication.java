package com.example.gpgserver;

import com.example.gpgserver.config.GpgProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GpgProperties.class)
public class GpgApplication {

    public static void main(String[] args) {
        SpringApplication.run(GpgApplication.class, args);
    }
}
