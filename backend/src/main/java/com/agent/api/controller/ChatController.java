package com.agent.api.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agent.agentcore.service.ReActAgentService;
import com.agent.auth.mapper.ChatRecordMapper;
import com.agent.common.Result;
import com.agent.common.dto.ChatRequest;
import com.agent.common.dto.ChatResponse;
import com.agent.memory.service.ShortTermMemoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * REST controller for chat endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "Endpoints for AI agent conversations")
public class ChatController {

    private final ReActAgentService reActAgentService;
    private final ShortTermMemoryService shortTermMemoryService;

    // Dedicated thread pool for SSE streaming tasks (not injected, created locally)
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @PostMapping
    @Operation(summary = "Send a message")
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: sessionId={}, message='{}'",
                request.getSessionId(),
                request.getMessage().substring(0, Math.min(request.getMessage().length(), 100)));

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        Long kbId = null;
        if (request.getKnowledgeBaseId() != null && !request.getKnowledgeBaseId().isBlank()) {
            try {
                kbId = Long.parseLong(request.getKnowledgeBaseId());
            } catch (NumberFormatException e) {
                log.warn("Invalid knowledgeBaseId: {}", request.getKnowledgeBaseId());
            }
        }

        String answer = reActAgentService.chat(request.getMessage(), sessionId, kbId);

        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setSessionId(sessionId);
        response.setToolsUsed(List.of());
        response.setSources(List.of());

        return Result.success(response);
    }

    /**
     * SSE streaming endpoint using SseEmitter.
     *
     * SseEmitter is the correct Spring MVC approach for server-sent events.
     * Unlike returning Flux<String> (which is a Spring WebFlux concept and gets
     * collected into a single response in a servlet container), SseEmitter
     * keeps the HTTP connection open and sends each event immediately as it
     * is produced on the background thread.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a response via SSE")
    public SseEmitter streamChat(
            @RequestParam String message,
            @RequestParam String sessionId,
            @RequestParam(required = false) Long kbId,
            HttpServletResponse httpResponse) {

        log.info("SSE stream request: sessionId={}, message='{}'",
                sessionId, message.substring(0, Math.min(message.length(), 100)));

        // Disable proxy / CDN buffering
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Cache-Control", "no-cache, no-store");
        httpResponse.setHeader("Connection", "keep-alive");

        // 5-minute timeout (GLM-4 can be slow); 0 = infinite
        SseEmitter emitter = new SseEmitter(300_000L);

        // Run the blocking LLM call + streaming on a background thread so the
        // servlet thread is released immediately.
        final String finalSessionId = sessionId;
        final Long finalKbId = kbId;

        sseExecutor.execute(() ->
            reActAgentService.streamWithEmitter(emitter, message, finalSessionId, finalKbId)
        );

        return emitter;
    }

    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get chat history")
    public Result<List<Map<String, String>>> getChatHistory(@PathVariable String sessionId) {
        List<Map<String, String>> history = shortTermMemoryService.getHistory(sessionId);
        return Result.success(history);
    }

    @DeleteMapping("/memory/{sessionId}")
    @Operation(summary = "Clear memory")
    public Result<Void> clearMemory(@PathVariable String sessionId) {
        shortTermMemoryService.clearMemory(sessionId);
        return Result.success();
    }
}
