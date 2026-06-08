package com.property.repair;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.property.repair.mapper")
public class RepairSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepairSystemApplication.class, args);
    }
}
