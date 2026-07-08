package com.agent.agentcore.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AI Service interface for the enterprise intelligent Q&A agent.
 * <p>
 * This interface uses LangChain4j's declarative AI Services pattern to replace
 * the previous hand-written ReAct agent pipeline. The framework automatically handles:
 * <ul>
 *   <li>System prompt injection via {@code SystemMessageProvider} (configured in AgentConfig)</li>
 *   <li>Conversation memory management via {@link MemoryId} and {@code chatMemoryProvider}</li>
 *   <li>Tool/function calling via registered {@code @Tool}-annotated beans</li>
 *   <li>Streaming responses via {@link TokenStream}</li>
 * </ul>
 * <p>
 * The system prompt is dynamically selected at runtime by the {@code SystemMessageProvider}:
 * when knowledge base (RAG) context is available, a KB-focused prompt is used that does
 * NOT mention any tools — this prevents the LLM from defaulting to tool-seeking mode
 * and ignoring the provided KB content. When no KB context is available, a tool-enabled
 * prompt is used that guides the LLM to use available tools.
 */
public interface AgentAssistant {

    /**
     * Processes a user message and returns a complete response.
     * <p>
     * LangChain4j automatically:
     * <ul>
     *   <li>Loads conversation history for the given {@code sessionId}</li>
     *   <li>Selects the system prompt dynamically (KB-mode vs tool-mode via SystemMessageProvider)</li>
     *   <li>Calls registered tools when the LLM decides they are needed</li>
     *   <li>Saves the assistant's response back to memory</li>
     * </ul>
     *
     * @param sessionId   the conversation session identifier for memory isolation
     * @param ragContext  knowledge base context (empty string if no KB is selected)
     * @param userMessage the user's input message
     * @return the LLM-generated response
     */
    @UserMessage("{{context}}\n\n{{userMessage}}")
    String chat(@MemoryId String sessionId, @V("context") String ragContext, @V("userMessage") String userMessage);

    /**
     * Processes a user message and returns a streaming response via {@link TokenStream}.
     * <p>
     * Same semantics as {@link #chat(String, String, String)} but returns tokens
     * progressively for real-time display in the frontend.
     *
     * @param sessionId   the conversation session identifier
     * @param ragContext  knowledge base context (empty string if no KB is selected)
     * @param userMessage the user's input message
     * @return a TokenStream that emits response tokens as they are generated
     */
    @UserMessage("{{context}}\n\n{{userMessage}}")
    TokenStream chatStream(@MemoryId String sessionId, @V("context") String ragContext, @V("userMessage") String userMessage);
}
