package com.property.repair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileUploadConfig {

    private String uploadDir = "./uploads";

    private String maxSize = "10MB";
}
