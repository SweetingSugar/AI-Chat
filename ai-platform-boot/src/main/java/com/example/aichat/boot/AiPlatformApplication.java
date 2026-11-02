package com.example.aichat.boot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.aichat")
@MapperScan(
        basePackages = {"com.example.aichat.system.mapper", "com.example.aichat.ai.mapper", "com.example.aichat.chat.mapper",
                "com.example.aichat.admin.mapper", "com.example.aichat.file.mapper"},
        sqlSessionTemplateRef = "sqlSessionTemplate")
public class AiPlatformApplication {

    private static final Log log = LogFactory.getLog(AiPlatformApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
        log.info("AiPlatformApplication started");
    }
}
