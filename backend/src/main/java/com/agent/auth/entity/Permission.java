package com.agent.auth.entity;

import lombok.Getter;

/**
 * Permission enumeration defining fine-grained access control.
 * Each permission carries a human-readable description.
 */
@Getter
public enum Permission {

    KB_MANAGE("Manage knowledge bases"),
    KB_SEARCH("Search knowledge bases"),
    CHAT("Chat with AI agent"),
    CHAT_HISTORY_VIEW("View chat history"),
    CHAT_HISTORY_DELETE("Delete chat history"),
    USER_MANAGE("Manage users");

    /** Human-readable description of the permission */
    private final String description;

    Permission(String description) {
        this.description = description;
    }
}
