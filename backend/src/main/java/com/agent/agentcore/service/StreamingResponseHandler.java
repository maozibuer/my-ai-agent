package com.agent.agentcore.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Helper component for SSE (Server-Sent Events) streaming responses.
 * Provides methods to convert a full response into a stream of chunks
 * and to stream responses directly from the LLM.
 */
@Slf4j
@Component
public class StreamingResponseHandler {

    /** Size of each text chunk emitted during simulated streaming */
    private static final int CHUNK_SIZE = 8;

    /** Delay between chunks in milliseconds */
    private static final long CHUNK_DELAY_MS = 30;

    /**
     * Converts a full response string into a Flux of text chunks,
     * simulating a streaming response.
     *
     * @param fullResponse the complete response text
     * @return a Flux emitting chunks of the response
     */
    public Flux<String> toStream(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return Flux.empty();
        }

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < fullResponse.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, fullResponse.length());
            chunks.add(fullResponse.substring(i, end));
        }

        log.debug("Splitting response of {} chars into {} chunks", fullResponse.length(), chunks.size());

        return Flux.fromIterable(chunks)
                .delayElements(Duration.ofMillis(CHUNK_DELAY_MS));
    }

    /**
     * Calls the LLM with the given prompt and streams the response.
     * Since the standard ChatLanguageModel does not support true streaming,
     * the full response is generated first and then chunked for streaming.
     *
     * @param model  the chat language model to use
     * @param prompt the prompt to send to the model
     * @return a Flux emitting chunks of the model's response
     */
    public Flux<String> streamFromModel(ChatLanguageModel model, String prompt) {
        log.info("Streaming response from model for prompt of length: {}", prompt.length());

        return Flux.create(sink -> {
            try {
                String response = model.generate(prompt);
                if (response == null || response.isEmpty()) {
                    sink.next("Empty response from model.");
                } else {
                    // Emit chunks
                    for (int i = 0; i < response.length(); i += CHUNK_SIZE) {
                        int end = Math.min(i + CHUNK_SIZE, response.length());
                        sink.next(response.substring(i, end));
                    }
                }
                sink.complete();
                log.info("Streaming completed, total length: {}",
                        response != null ? response.length() : 0);
            } catch (Exception e) {
                log.error("Error during model streaming", e);
                sink.next("Error: " + e.getMessage());
                sink.complete();
            }
        });
    }
}
