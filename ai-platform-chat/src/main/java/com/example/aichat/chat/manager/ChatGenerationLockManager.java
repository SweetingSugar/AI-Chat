package com.example.aichat.chat.manager;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatGenerationLockManager {
    private final ConcurrentMap<Long, AtomicBoolean> locks = new ConcurrentHashMap<>();

    public boolean tryAcquire(Long sessionId) {
        return locks.computeIfAbsent(sessionId, ignored -> new AtomicBoolean()).compareAndSet(false, true);
    }

    public void release(Long sessionId) {
        AtomicBoolean lock = locks.get(sessionId);
        if (lock == null || !lock.compareAndSet(true, false)) return;
        locks.remove(sessionId, lock);
    }
}
