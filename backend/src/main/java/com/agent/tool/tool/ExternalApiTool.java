package com.agent.tool.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool for calling external API endpoints.
 * Uses Spring's RestClient to make HTTP requests and return response bodies.
 */
@Slf4j
@Component
public class ExternalApiTool {

    private final RestClient restClient;

    /**
     * Constructs the ExternalApiTool with a configured RestClient.
     */
    public ExternalApiTool() {
        this.restClient = RestClient.builder()
                .build();
    }

    /**
     * Calls an external API endpoint and returns the response body.
     *
     * @param url    the API URL to call
     * @param method the HTTP method (GET, POST, PUT, DELETE)
     * @return the response body as a string
     */
    @Tool("Call an external API endpoint and return the response")
    public String callExternalApi(@P("API URL") String url, @P("HTTP method: GET, POST, PUT, DELETE") String method) {
        log.info("External API tool called: {} {}", method, url);

        if (url == null || url.isBlank()) {
            return "Error: API URL is required.";
        }

        String httpMethod = method != null ? method.trim().toUpperCase() : "GET";

        try {
            String response = switch (httpMethod) {
                case "GET" -> restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                case "POST" -> restClient.post()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                case "PUT" -> restClient.put()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                case "DELETE" -> restClient.delete()
                        .uri(url)
                        .retrieve()
                        .body(String.class);
                default -> "Error: Unsupported HTTP method: " + httpMethod +
                        ". Supported methods: GET, POST, PUT, DELETE.";
            };

            log.info("External API call successful: {} {}", method, url);
            return response != null ? response : "Empty response body.";
        } catch (Exception e) {
            log.error("External API call failed: {} {} - {}", method, url, e.getMessage());
            return "Error calling external API: " + e.getMessage();
        }
    }
}
