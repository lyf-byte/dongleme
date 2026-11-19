package com.dongleme.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.dongleme.backend.mapper")
public class DonglemeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DonglemeApplication.class, args);
    }
}
