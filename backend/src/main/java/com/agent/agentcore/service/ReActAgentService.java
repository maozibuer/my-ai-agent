package com.agent.agentcore.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agent.knowledge.service.HybridSearchService.SearchResult;
import com.agent.knowledge.service.KnowledgeBaseService;
import com.agent.memory.service.HotAnswerCacheService;
import com.agent.memory.service.ShortTermMemoryService;

import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Core agent service — now delegating to LangChain4j AI Services.
 * <p>
 * This class previously contained a hand-written 7-step agent pipeline with
 * keyword-based intent recognition, manual tool dispatch, hand-rolled prompt
 * construction, and custom SSE chunking. All of that has been replaced by
 * LangChain4j's declarative {@link AgentAssistant} AI Service, which handles:
 * <ul>
 *   <li>System prompt injection</li>
 *   <li>Conversation memory (via {@code @MemoryId} + {@code ChatMemoryProvider})</li>
 *   <li>LLM-driven tool/function calling (via registered {@code @Tool} beans)</li>
 *   <li>Native token streaming (via {@link TokenStream})</li>
 * </ul>
 * <p>
 * The only responsibilities remaining in this service are:
 * <ol>
 *   <li>Hot answer cache check</li>
 *   <li>RAG context retrieval from the knowledge base</li>
 *   <li>Delegation to the AI Service</li>
 *   <li>Post-response caching</li>
 * </ol>
 */
@Slf4j
@Service
public class ReActAgentService {

    private final AgentAssistant kbAgentAssistant;
    private final AgentAssistant toolAgentAssistant;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ShortTermMemoryService shortTermMemoryService;
    private final HotAnswerCacheService hotAnswerCacheService;

    /**
     * Default number of RAG results to retrieve
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * Minimum question length to consider for hot caching
     */
    private static final int MIN_CACHE_QUESTION_LENGTH = 10;

    /**
     * Constructs the service with all required dependencies.
     * <p>
     * Two dedicated {@link AgentAssistant} beans are provided:
     * {@code kbAgentAssistant} for knowledge-base mode (no tools) and
     * {@code toolAgentAssistant} for tool mode (weather, statistics, external API).
     * This avoids ThreadLocal-based system prompt selection.
     */
    public ReActAgentService(@Qualifier("kbAgentAssistant") AgentAssistant kbAgentAssistant,
                             @Qualifier("toolAgentAssistant") AgentAssistant toolAgentAssistant,
                             KnowledgeBaseService knowledgeBaseService,
                             ShortTermMemoryService shortTermMemoryService,
                             HotAnswerCacheService hotAnswerCacheService) {
        this.kbAgentAssistant = kbAgentAssistant;
        this.toolAgentAssistant = toolAgentAssistant;
        this.knowledgeBaseService = knowledgeBaseService;
        this.shortTermMemoryService = shortTermMemoryService;
        this.hotAnswerCacheService = hotAnswerCacheService;
    }

    /**
     * Processes a user message and returns a complete response.
     * <p>
     * Delegates to the LangChain4j AI Service, which handles memory,
     * tool calling, and LLM generation automatically.
     *
     * @param userMessage the user's input message
     * @param sessionId   the conversation session identifier
     * @param kbId        optional knowledge base ID for RAG-based answers
     * @return the generated response string
     */
    public String chat(String userMessage, String sessionId, Long kbId) {
        log.info("Agent processing: sessionId={}, kbId={}, message='{}'",
                sessionId, kbId, truncate(userMessage, 100));

        // Ensure session ID is set
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // Step 1: Check hot answer cache
        String cachedAnswer = hotAnswerCacheService.get(userMessage, kbId);
        if (cachedAnswer != null) {
            log.info("Hot cache hit, returning cached answer");
            return cachedAnswer;
        }

        // Step 2: Retrieve RAG context from knowledge base (if applicable)
        String ragContext = buildRagContext(userMessage, kbId);
        boolean hasRagContext = ragContext != null && !ragContext.isEmpty();
        AgentAssistant agentAssistant = selectAssistant(hasRagContext);
        log.info("chat: using {} (ragContext length={}, kbId={})",
                hasRagContext ? "KB_MODE_PROMPT" : "TOOL_MODE_PROMPT",
                ragContext.length(), kbId);

        // Step 3: Delegate to the appropriate LangChain4j AI Service
        String response;
        try {
            response = agentAssistant.chat(sessionId, ragContext, userMessage);
            log.debug("LLM generated response of length: {}",
                    response != null ? response.length() : 0);
        } catch (Exception e) {
            log.error("AI Service call failed", e);
            response = "抱歉，AI 服务暂时不可用，请稍后重试。错误信息：" + e.getMessage();
        }

        // Step 4: Cache if this is a potentially hot question
        if (userMessage.length() >= MIN_CACHE_QUESTION_LENGTH) {
            hotAnswerCacheService.put(userMessage, response, kbId);
        }

        return response;
    }

    /**
     * Processes a user message and returns a streaming response via SSE with
     * LangChain4j's native {@link TokenStream}.
     * <p>
     * This replaces the previous hand-rolled character-grouping approach with
     * the framework's built-in token-by-token streaming, which is both more
     * efficient and more natural (tokens instead of arbitrary character groups).
     *
     * @param userMessage the user's input message
     * @param sessionId   the conversation session identifier
     * @param kbId        optional knowledge base ID for RAG-based answers
     * @return a Flux of response string chunks for SSE streaming
     */
    public Flux<String> chatStream(String userMessage, String sessionId, Long kbId) {
        log.info("Agent streaming: sessionId={}, message='{}'",
                sessionId, truncate(userMessage, 100));

        final String resolvedSessionId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;

        return Flux.create(sink -> {
            try {
                // Send start marker
                sink.next("data: {\"content\":\"\",\"status\":\"processing\"}\n\n");

                // Check hot cache first
                String cachedAnswer = hotAnswerCacheService.get(userMessage, kbId);
                if (cachedAnswer != null) {
                    streamText(sink, cachedAnswer);
                    sink.next("data: {\"content\":\"\",\"done\":true}\n\n");
                    sink.complete();
                    return;
                }

                // Retrieve RAG context and select the appropriate assistant
                String ragContext = buildRagContext(userMessage, kbId);
                boolean hasRagContext = ragContext != null && !ragContext.isEmpty();
                AgentAssistant agentAssistant = selectAssistant(hasRagContext);
                log.info("chatStream: using {} (ragContext length={})",
                        hasRagContext ? "KB_MODE_PROMPT" : "TOOL_MODE_PROMPT", ragContext.length());

                // Use LangChain4j TokenStream for native token-by-token streaming
                TokenStream tokenStream = agentAssistant.chatStream(resolvedSessionId, ragContext, userMessage);

                CompletableFuture<String> futureResponse = new CompletableFuture<>();

                tokenStream
                        .onNext(token -> {
                            // Send each token as an SSE event
                            String escaped = escapeJson(token);
                            sink.next("data: {\"content\":\"" + escaped + "\"}\n\n");
                        })
                        .onComplete(response -> {
                            // Cache the complete response
                            String fullText = response.content().text();
                            if (userMessage.length() >= MIN_CACHE_QUESTION_LENGTH) {
                                hotAnswerCacheService.put(userMessage, fullText, kbId);
                            }
                            futureResponse.complete(fullText);
                        })
                        .onError(error -> {
                            log.error("TokenStream error: {}", error.getMessage(), error);
                            String errorMsg = "data: {\"content\":\"" + escapeJson("抱歉，生成回复时出现错误："
                                    + error.getMessage()) + "\",\"done\":true}\n\n";
                            sink.next(errorMsg);
                            futureResponse.completeExceptionally(error);
                        })
                        .start();

                // Wait for the stream to complete (blocking until done)
                try {
                    futureResponse.join();
                } catch (Exception e) {
                    log.error("Streaming future failed: {}", e.getMessage());
                }

                sink.next("data: {\"content\":\"\",\"done\":true}\n\n");
                sink.complete();

            } catch (Exception e) {
                log.error("Streaming failed: {}", e.getMessage(), e);
                try {
                    sink.next("data: {\"content\":\"" + escapeJson("服务异常，请稍后重试")
                            + "\",\"done\":true}\n\n");
                } catch (Exception ignored) {
                    // Sink may already be cancelled
                }
                sink.complete();
            }
        });
    }

    /**
     * SSE streaming via SseEmitter with LangChain4j {@link TokenStream}.
     * <p>
     * This replaces the previous manual character-grouping approach. Each token
     * from the LLM is sent as a separate SSE event for real-time display.
     */
    public void streamWithEmitter(SseEmitter emitter, String userMessage, String sessionId, Long kbId) {
        try {
            final String resolvedSessionId = (sessionId == null || sessionId.isBlank())
                    ? UUID.randomUUID().toString()
                    : sessionId;

            // Send a status event so the client knows we're working
            sendSseEvent(emitter, "{\"status\":\"processing\"}");

            // Check hot answer cache
            String cachedAnswer = hotAnswerCacheService.get(userMessage, kbId);
            if (cachedAnswer != null) {
                streamTokensToEmitter(emitter, cachedAnswer);
                sendSseEvent(emitter, "{\"done\":true}");
                emitter.complete();
                return;
            }

            // Retrieve RAG context and select the appropriate assistant
            String ragContext = buildRagContext(userMessage, kbId);
            boolean hasRagContext = ragContext != null && !ragContext.isEmpty();
            AgentAssistant agentAssistant = selectAssistant(hasRagContext);
            log.info("streamWithEmitter: using {} (ragContext length={})",
                    hasRagContext ? "KB_MODE_PROMPT" : "TOOL_MODE_PROMPT", ragContext.length());
            log.info("ragContext:{}", ragContext);
            // Use LangChain4j TokenStream
            TokenStream tokenStream = agentAssistant.chatStream(resolvedSessionId, ragContext, userMessage);

            CompletableFuture<Void> streamDone = new CompletableFuture<>();

            tokenStream
                    .onNext(token -> {
                        try {
                            sendSseEvent(emitter, "{\"content\":\"" + escapeJson(token) + "\"}");
                        } catch (IOException e) {
                            log.error("Failed to send SSE token: {}", e.getMessage());
                            streamDone.completeExceptionally(e);
                        }
                    })
                    .onComplete(response -> {
                        // Cache the complete response
                        String fullText = response.content().text();
                        if (userMessage.length() >= MIN_CACHE_QUESTION_LENGTH) {
                            hotAnswerCacheService.put(userMessage, fullText, kbId);
                        }
                        streamDone.complete(null);
                    })
                    .onError(error -> {
                        log.error("TokenStream error in emitter: {}", error.getMessage(), error);
                        try {
                            sendSseEvent(emitter, "{\"content\":\""
                                    + escapeJson("抱歉，生成回复时出现错误") + "\",\"done\":true}");
                        } catch (IOException ignored) {
                        }
                        streamDone.completeExceptionally(error);
                    })
                    .start();

            // Wait for streaming to finish
            try {
                streamDone.join();
            } catch (Exception e) {
                log.error("Emitter streaming future failed: {}", e.getMessage());
            }

            sendSseEvent(emitter, "{\"done\":true}");
            emitter.complete();

        } catch (Exception e) {
            log.error("SseEmitter streaming failed: {}", e.getMessage(), e);
            try {
                sendSseEvent(emitter, "{\"content\":\"服务异常，请稍后重试\",\"done\":true}");
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    // ──────────────────────────────────────────────
    // Private helper methods
    // ──────────────────────────────────────────────

    /**
     * Selects the appropriate AgentAssistant based on whether RAG context is available.
     * <p>
     * Using two dedicated assistants removes the need for a ThreadLocal flag and
     * guarantees that the KB path never sees tool definitions, while the tool path
     * always has its tools available.
     */
    private AgentAssistant selectAssistant(boolean hasRagContext) {
        return hasRagContext ? kbAgentAssistant : toolAgentAssistant;
    }

    /**
     * Retrieves RAG context from the knowledge base and formats it for the AI Service.
     * <p>
     * Returns the raw knowledge-base content chunks (joined as a bulleted list).
     * This string is passed to {@link AgentAssistant#chat} as the
     * {@code @V("context")} parameter, which LangChain4j substitutes into the
     * {@code {{context}}} placeholder inside the {@code KB_MODE_PROMPT} system
     * prompt (defined in {@link com.agent.agentcore.config.AgentConfig}).
     * <p>
     * All answering instructions live in {@code KB_MODE_PROMPT} — this method
     * only provides the data, not the instructions.
     *
     * @param userMessage the user's query (used for retrieval, NOT embedded in output)
     * @param kbId        the knowledge base ID, or null
     * @return a formatted context string with KB content, or empty string if no KB content
     */
    private String buildRagContext(String userMessage, Long kbId) {
        if (kbId == null) {
            return "";
        }

        List<SearchResult> results;
        try {
            results = knowledgeBaseService.search(userMessage, DEFAULT_TOP_K, kbId);
        } catch (Exception e) {
            log.warn("Knowledge base search failed (ES may be unavailable): {}", e.getMessage());
            return "";
        }

        log.info("RAG retrieval for kbId={}: found {} chunks", kbId, results.size());

        if (results.isEmpty()) {
            return "";
        }

        List<String> chunks = results.stream()
                .map(SearchResult::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();

        if (chunks.isEmpty()) {
            log.info("RAG found no relevant content for kbId={}, all chunks were null/blank", kbId);
            return "";
        }

        String kbContent = chunks.stream()
                .map(chunk -> "- " + chunk)
                .collect(Collectors.joining("\n"));

        log.info("buildRagContext: {} chunks with content for kbId={}, total length={}",
                chunks.size(), kbId, kbContent.length());

        // Return only the raw KB content chunks. These are injected into the
        // KB_MODE_PROMPT system prompt via the {{context}} placeholder (which
        // LangChain4j substitutes with the @V("context") parameter value).
        // All answering instructions live in KB_MODE_PROMPT — do NOT duplicate
        // them here, and do NOT mention "tools" (pink elephant effect).
        return kbContent;
    }

    /**
     * Streams complete text as individual SSE events using a simple approach.
     * Used only for cached responses (where TokenStream is not applicable).
     */
    private void streamText(FluxSink<String> sink, String text) {
        if (text == null || text.isEmpty()) return;

        final int GROUP = 3;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);

            boolean sentenceEnd = (c == '。' || c == '！' || c == '？' || c == '\n');
            boolean groupFull = buf.length() >= GROUP;

            if (sentenceEnd || groupFull || i == text.length() - 1) {
                sink.next("data: {\"content\":\"" + escapeJson(buf.toString()) + "\"}\n\n");
                buf.setLength(0);
                try {
                    Thread.sleep(sentenceEnd ? 100 : 40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Streams text tokens to an SseEmitter. Used for cached responses.
     */
    private void streamTokensToEmitter(SseEmitter emitter, String text) throws IOException {
        if (text == null || text.isEmpty()) return;

        final int GROUP = 3;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);

            boolean sentenceEnd = (c == '。' || c == '！' || c == '？' || c == '\n');
            boolean groupFull = buf.length() >= GROUP;

            if (sentenceEnd || groupFull || i == text.length() - 1) {
                sendSseEvent(emitter, "{\"content\":\"" + escapeJson(buf.toString()) + "\"}");
                buf.setLength(0);
                try {
                    Thread.sleep(sentenceEnd ? 100 : 40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Send one SSE data line via SseEmitter.
     */
    private void sendSseEvent(SseEmitter emitter, String jsonPayload) throws IOException {
        emitter.send(SseEmitter.event().data(jsonPayload, MediaType.APPLICATION_JSON));
    }

    /**
     * Escapes special characters for JSON embedding.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Truncates a string to the specified max length for logging.
     */
    private String truncate(String s, int maxLen) {
        return s.substring(0, Math.min(s.length(), maxLen));
    }

}
