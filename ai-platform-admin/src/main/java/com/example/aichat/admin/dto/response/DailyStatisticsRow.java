package com.example.aichat.admin.dto.response;

public class DailyStatisticsRow {
    private String statDate;
    private Long newUsers;
    private Long activeUsers;
    private Long newSessions;
    private Long messages;
    private Long userMessages;
    private Long assistantMessages;
    private Long completedMessages;
    private Long failedMessages;
    private Long generationTasks;
    private Long successfulGenerationTasks;
    private Long aiCalls;
    private Long successfulAiCalls;
    private Long totalTokens;

    public String getStatDate() {
        return statDate;
    }

    public void setStatDate(String statDate) {
        this.statDate = statDate;
    }

    public Long getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(Long newUsers) {
        this.newUsers = newUsers;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Long getNewSessions() {
        return newSessions;
    }

    public void setNewSessions(Long newSessions) {
        this.newSessions = newSessions;
    }

    public Long getMessages() {
        return messages;
    }

    public void setMessages(Long messages) {
        this.messages = messages;
    }

    public Long getUserMessages() {
        return userMessages;
    }

    public void setUserMessages(Long userMessages) {
        this.userMessages = userMessages;
    }

    public Long getAssistantMessages() {
        return assistantMessages;
    }

    public void setAssistantMessages(Long assistantMessages) {
        this.assistantMessages = assistantMessages;
    }

    public Long getCompletedMessages() {
        return completedMessages;
    }

    public void setCompletedMessages(Long completedMessages) {
        this.completedMessages = completedMessages;
    }

    public Long getFailedMessages() {
        return failedMessages;
    }

    public void setFailedMessages(Long failedMessages) {
        this.failedMessages = failedMessages;
    }

    public Long getGenerationTasks() {
        return generationTasks;
    }

    public void setGenerationTasks(Long generationTasks) {
        this.generationTasks = generationTasks;
    }

    public Long getSuccessfulGenerationTasks() {
        return successfulGenerationTasks;
    }

    public void setSuccessfulGenerationTasks(Long successfulGenerationTasks) {
        this.successfulGenerationTasks = successfulGenerationTasks;
    }

    public Long getAiCalls() {
        return aiCalls;
    }

    public void setAiCalls(Long aiCalls) {
        this.aiCalls = aiCalls;
    }

    public Long getSuccessfulAiCalls() {
        return successfulAiCalls;
    }

    public void setSuccessfulAiCalls(Long successfulAiCalls) {
        this.successfulAiCalls = successfulAiCalls;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }
}
