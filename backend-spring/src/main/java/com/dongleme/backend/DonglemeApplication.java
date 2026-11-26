package com.dongleme.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@MapperScan("com.dongleme.backend.mapper")
public class DonglemeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DonglemeApplication.class, args);
    }
}
