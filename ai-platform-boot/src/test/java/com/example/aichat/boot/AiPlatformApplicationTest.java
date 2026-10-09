package com.example.aichat.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class AiPlatformApplicationTest {

    @Test
    void declaresTheBootApplicationInTheSharedPackageRoot() {
        SpringBootApplication application = AiPlatformApplication.class.getAnnotation(SpringBootApplication.class);

        assertNotNull(application);
        assertEquals("com.example.aichat", application.scanBasePackages()[0]);
    }
}
