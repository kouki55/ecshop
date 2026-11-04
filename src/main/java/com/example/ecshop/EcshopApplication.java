package com.example.ecshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// @ConfigurationProperties を付けた record（AuthProperties など）を自動で読み込む
@ConfigurationPropertiesScan
public class EcshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcshopApplication.class, args);
    }
}
