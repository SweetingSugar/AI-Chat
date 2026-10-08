package com.example.aichat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiChatApplication {

    private static final Log log = LogFactory.getLog(AiChatApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
        log.info("AiChatApplication started");
    }

}
