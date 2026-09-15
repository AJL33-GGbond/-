package com.letter.model;

import java.time.LocalDateTime;

public class Letter {
    private final long id;
    private final String content;
    private final String status;
    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;
    private final LocalDateTime sentAt;

    public Letter(long id, String content, String status, LocalDateTime createdAt,
                  LocalDateTime updatedAt, LocalDateTime sentAt) {
        this.id = id;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sentAt = sentAt;
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSentAt() { return sentAt; }

    public long getId() { return id; }
    public String getContent() { return content; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
