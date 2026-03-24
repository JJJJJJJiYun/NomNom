package com.nomnom.mvp.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.nomnom.mvp")
public class NomNomApplication {
    public static void main(String[] args) {
        SpringApplication.run(NomNomApplication.class, args);
    }
}
