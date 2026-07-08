package com.agent.agentcore.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Service for managing prompt templates used by the ReAct agent.
 * Provides methods for generating system prompts, RAG prompts, and context-wrapped prompts.
 *
 * @deprecated Since the migration to LangChain4j AI Services, the system prompt
 *             is defined in the {@code @SystemMessage} annotation on
 *             {@link AgentAssistant}, and RAG context is injected via
 *             {@code @V("context")}. The prompt building logic has moved to
 *             {@link ReActAgentService#buildRagContext(String, Long)}.
 *             This class is retained for reference only.
 */
@Deprecated
@Service
public class PromptManager {

    /** System prompt defining the agent's role and behavior */
    private static final String SYSTEM_PROMPT = """
            You are an enterprise-level intelligent Q&A assistant.
            Use the available tools and knowledge base to answer questions.
            Think step by step.

            Guidelines:
            1. If the question is about weather, statistics, or external data, use the appropriate tool.
            2. If the question requires knowledge base information, use the provided context.
            3. If no tool or knowledge base is needed, answer directly.
            4. Always provide clear, accurate, and helpful responses.
            5. If you are unsure, state that clearly rather than guessing.
            """;

    /**
     * Returns the system prompt for the ReAct agent.
     *
     * @return the system prompt string
     */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Builds a RAG (Retrieval-Augmented Generation) prompt with context and question.
     *
     * @param context  the retrieved context from the knowledge base
     * @param question the user's question
     * @return the assembled RAG prompt string
     */
    public String buildRagPrompt(String context, String question) {
        return """
                Based on the following context, please answer the question.
                If the context does not contain relevant information, say so and answer based on your general knowledge.

                Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(context, question);
    }

    /**
     * Wraps a user question with context chunks from the knowledge base.
     *
     * @param question      the user's question
     * @param contextChunks the list of retrieved context chunks
     * @return the context-wrapped prompt string
     */
    public String wrapWithContext(String question, List<String> contextChunks) {
        if (contextChunks == null || contextChunks.isEmpty()) {
            return question;
        }

        String context = contextChunks.stream()
                .map(chunk -> "- " + chunk)
                .collect(Collectors.joining("\n"));

        return """
                请根据以下知识库内容回答用户问题。
                如果知识库内容不足以回答问题，请说明无法从知识库中找到相关信息，并基于通用知识给出最佳回答。
                请用中文回答。

                【知识库内容】
                %s

                【用户问题】
                %s

                【回答】
                """.formatted(context, question);
    }
}
