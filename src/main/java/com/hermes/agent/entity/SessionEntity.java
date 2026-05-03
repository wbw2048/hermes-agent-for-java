package com.hermes.agent.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 会话实体，映射 sessions 表。
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

    @Id
    private String id;

    @Column(nullable = true)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "workspace_dir")
    private String workspaceDir;

    public SessionEntity() {}

    public SessionEntity(String id, String title, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public void incrementMessageCount() { this.messageCount++; }

    public String getWorkspaceDir() { return workspaceDir; }
    public void setWorkspaceDir(String workspaceDir) { this.workspaceDir = workspaceDir; }
}
