package com.example.aichat.chat.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatGenerationLockManagerTest {
    @Test
    void sameSessionCannotRunTwoGenerationsAtOnce() {
        ChatGenerationLockManager manager = new ChatGenerationLockManager();

        assertTrue(manager.tryAcquire(10L));
        assertFalse(manager.tryAcquire(10L));

        manager.release(10L);

        assertTrue(manager.tryAcquire(10L));
        manager.release(10L);
    }
}
