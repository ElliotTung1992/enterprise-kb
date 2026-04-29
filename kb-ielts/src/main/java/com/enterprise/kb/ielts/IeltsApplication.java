package com.enterprise.kb.ielts;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.enterprise.kb.ielts")
@MapperScan("com.enterprise.kb.ielts.mapper")
public class IeltsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IeltsApplication.class, args);
    }
}
