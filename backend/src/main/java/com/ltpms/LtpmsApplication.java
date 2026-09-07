package com.ltpms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LTPMS 应用启动类。
 */
@EnableScheduling
@SpringBootApplication
public class LtpmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LtpmsApplication.class, args);
    }
}
