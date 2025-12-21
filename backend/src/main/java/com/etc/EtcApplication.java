package com.etc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EtcApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtcApplication.class, args);
    }
}
