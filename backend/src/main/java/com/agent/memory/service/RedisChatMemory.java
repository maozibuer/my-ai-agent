package com.agent.memory.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter that wraps {@link ShortTermMemoryService} to implement the
 * LangChain4j {@link ChatMemory} interface.
 * <p>
 * This allows the ReAct agent to use Redis-backed conversation history
 * transparently through the standard LangChain4j memory abstraction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    private final ShortTermMemoryService shortTermMemoryService;

    /** Session ID is set dynamically per conversation */
    private String sessionId = "default";

    /**
     * Sets the session ID for this chat memory instance.
     *
     * @param sessionId the chat session identifier
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns the unique identifier for this chat memory instance.
     * Uses the session ID as the memory identifier.
     *
     * @return the session ID
     */
    @Override
    public Object id() {
        return sessionId;
    }

    /**
     * Adds a chat message to the Redis-backed conversation memory.
     * The message role is determined by the message type (UserMessage or AiMessage).
     *
     * @param message the chat message to add
     */
    @Override
    public void add(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            shortTermMemoryService.saveMessage(sessionId, "user", userMessage.text());
        } else if (message instanceof AiMessage aiMessage) {
            shortTermMemoryService.saveMessage(sessionId, "assistant", aiMessage.text());
        } else {
            log.warn("Unsupported message type: {}", message.getClass().getSimpleName());
        }
    }

    /**
     * Retrieves the conversation history from Redis and converts it
     * to a list of LangChain4j ChatMessage objects.
     *
     * @return a list of chat messages in chronological order
     */
    @Override
    public List<ChatMessage> messages() {
        List<Map<String, String>> history = shortTermMemoryService.getHistory(sessionId);
        List<ChatMessage> messages = new ArrayList<>();

        for (Map<String, String> entry : history) {
            String role = entry.get("role");
            String content = entry.get("content");

            if ("user".equalsIgnoreCase(role)) {
                messages.add(UserMessage.from(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                messages.add(AiMessage.from(content));
            }
        }

        return messages;
    }

    /**
     * Clears all conversation memory for the current session.
     */
    @Override
    public void clear() {
        shortTermMemoryService.clearMemory(sessionId);
    }
}
