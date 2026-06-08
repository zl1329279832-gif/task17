package com.property.repair;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.property.repair.mapper")
@EnableScheduling
public class PropertyRepairApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyRepairApplication.class, args);
    }
}
