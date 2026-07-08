package com.agent.agentcore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agent.agentcore.service.AgentAssistant;
import com.agent.memory.service.RedisChatMemory;
import com.agent.memory.service.ShortTermMemoryService;
import com.agent.tool.tool.DataStatisticsTool;
import com.agent.tool.tool.ExternalApiTool;
import com.agent.tool.tool.WeatherTool;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for the AI agent core components.
 * code by th3way:2385313282@qq.com
 * <p>
 * Provides beans for:
 * <ul>
 *   <li>{@link ChatLanguageModel} — blocking (non-streaming) LLM calls</li>
 *   <li>{@link dev.langchain4j.model.chat.StreamingChatLanguageModel} — token-by-token streaming LLM calls</li>
 *   <li>{@link AgentAssistant} — LangChain4j AI Service (declarative, replaces hand-written agent)</li>
 *   <li>{@link ChatMemory} — Redis-backed conversation memory</li>
 *   <li>{@link EmbeddingModel} — text embedding for RAG / knowledge base retrieval</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AgentConfig {

    /**
     * Creates the ChatLanguageModel bean for blocking (non-streaming) LLM calls.
     * Used by the AgentAssistant for the {@code chat()} method.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${langchain4j.open-ai.model-name}") String modelName) {
        log.info("Initializing ChatLanguageModel: model={}, baseUrl={}", modelName, baseUrl);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2000)
                .build();
    }

    /**
     * Creates the StreamingChatLanguageModel bean for token-by-token streaming.
     * Used by the AgentAssistant for the {@code chatStream()} method which returns
     * a {@link dev.langchain4j.service.TokenStream}.
     */
    @Bean
    public dev.langchain4j.model.chat.StreamingChatLanguageModel streamingChatLanguageModel(
            @Value("${langchain4j.open-ai.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${langchain4j.open-ai.model-name}") String modelName) {
        log.info("Initializing StreamingChatLanguageModel: model={}, baseUrl={}", modelName, baseUrl);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2000)
                .build();
    }

    /**
     * Creates the AgentAssistant AI Service bean used when a knowledge base is selected.
     * <p>
     * This assistant uses the KB-focused system prompt and has NO tools registered.
     * Keeping tools away from the KB path prevents the LLM from being distracted by
     * tool definitions when it should answer purely from the retrieved context.
     */
    @Bean(name = "kbAgentAssistant")
    public AgentAssistant kbAgentAssistant(
            ChatLanguageModel chatLanguageModel,
            dev.langchain4j.model.chat.StreamingChatLanguageModel streamingChatLanguageModel,
            ShortTermMemoryService shortTermMemoryService) {
        log.info("Initializing KB AgentAssistant AI Service with LangChain4j AiServices");

        return AiServices.builder(AgentAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(memoryId -> {
                    RedisChatMemory memory = new RedisChatMemory(shortTermMemoryService);
                    memory.setSessionId((String) memoryId);
                    return memory;
                })
                .systemMessageProvider(memoryId -> KB_MODE_PROMPT)
                .build();
    }

    /**
     * Creates the AgentAssistant AI Service bean used when no knowledge base is selected.
     * <p>
     * This assistant registers the available tools and guides the LLM to use them
     * when appropriate. It never sees knowledge-base context.
     */
    @Bean(name = "toolAgentAssistant")
    public AgentAssistant toolAgentAssistant(
            ChatLanguageModel chatLanguageModel,
            dev.langchain4j.model.chat.StreamingChatLanguageModel streamingChatLanguageModel,
            ShortTermMemoryService shortTermMemoryService,
            WeatherTool weatherTool,
            DataStatisticsTool dataStatisticsTool,
            ExternalApiTool externalApiTool) {
        log.info("Initializing Tool AgentAssistant AI Service with LangChain4j AiServices");

        return AiServices.builder(AgentAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemoryProvider(memoryId -> {
                    RedisChatMemory memory = new RedisChatMemory(shortTermMemoryService);
                    memory.setSessionId((String) memoryId);
                    return memory;
                })
                .systemMessageProvider(memoryId -> TOOL_MODE_PROMPT)
                .tools(weatherTool, dataStatisticsTool, externalApiTool)
                .build();
    }

    // ──────────────────────────────────────────────
    // Dynamic system prompts — selected at runtime
    // ──────────────────────────────────────────────

    /**
     * System prompt for knowledge base (RAG) mode.
     * <p>
     * IMPORTANT: This prompt NEVER mentions "tools" — not even in negative form
     * ("don't mention tools"). This avoids the "pink elephant effect" where
     * mentioning the word "tool" activates the LLM's tool-seeking behavior.
     * The prompt focuses entirely on answering from the provided reference materials.
     */
    private static final String KB_MODE_PROMPT = """
            你是一个专业的知识库问答助手。你的唯一任务是根据用户消息中提供的知识库参考资料来回答问题。

            每条用户消息的开头部分（在用户实际问题之前）会有一段以"- "开头的知识库参考资料条目，这些是从知识库中检索到的最相关内容。你必须严格依据这些参考资料回答，不要调用任何工具，也不要依赖你的通用知识。

            回答规则：
            1. 仔细阅读用户消息开头的知识库参考资料，提取与用户问题相关的信息。
            2. 如果参考资料包含答案，必须直接、清晰、准确地回答，不要绕弯子。
            3. 如果参考资料部分包含答案，回答能回答的部分，并说明哪些信息未在知识库中找到。
            4. 如果参考资料完全没有提及用户问题的相关内容，只回答："根据提供的知识库内容，未找到相关信息。"
            5. 禁止回答"很抱歉，我无法提供"等笼统拒绝语句。
            6. 始终使用中文回答。
            7. 回答要简洁明了，不要添加无关的内容。

            你是一个知识库助手，基于提供的参考资料回答问题，请直接给出你的回答。
            """;

    /**
     * System prompt for tool mode (no knowledge base context available).
     * <p>
     * This prompt explicitly lists the available tools and guides the LLM to
     * use them when appropriate.
     */
    private static final String TOOL_MODE_PROMPT = """
            You are an enterprise-level intelligent Q&A assistant.
            You have access to the following tools:
            - getWeather: query weather information for a specified city
            - getStatistics: query business data statistics (users, orders, revenue, products)
            - callExternalApi: call an external API endpoint

            Guidelines:
            1. Use the appropriate tool when the user asks about weather, statistics, or external data.
            2. If no tool is needed, answer directly from your general knowledge.
            3. Think step by step before answering.
            4. Always provide clear, accurate, and helpful responses in the user's language.
            5. If you are unsure, state that clearly rather than guessing.
            """;

    /**
     * Creates a standalone ChatMemory bean backed by Redis.
     * This is retained for potential direct use outside the AI Services pipeline
     * (e.g., manual memory inspection or cleanup). The primary memory management
     * for conversations is handled by the {@code chatMemoryProvider} inside
     * {@link #kbAgentAssistant} and {@link #toolAgentAssistant}.
     */
    @Bean
    public ChatMemory chatMemory(ShortTermMemoryService shortTermMemoryService) {
        log.info("Initializing standalone Redis-backed ChatMemory");
        return new RedisChatMemory(shortTermMemoryService);
    }

    /**
     * Creates the EmbeddingModel bean for text embedding (RAG / knowledge base).
     */
    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${langchain4j.open-ai.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:https://api.openai.com/v1}") String baseUrl) {
        log.info("Initializing EmbeddingModel: baseUrl={}", baseUrl);

        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }
}
