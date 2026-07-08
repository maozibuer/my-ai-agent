package com.agent.tool.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agent.tool.tool.DataStatisticsTool;
import com.agent.tool.tool.ExternalApiTool;
import com.agent.tool.tool.WeatherTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for dispatching tool calls based on recognized intent.
 * Acts as an intermediary between the ReAct agent and the individual tool components.
 *
 * @deprecated Since the migration to LangChain4j AI Services, tool dispatch is
 *             handled automatically by the LLM via function calling. The
 *             {@code @Tool}-annotated methods on {@code WeatherTool},
 *             {@code DataStatisticsTool}, and {@code ExternalApiTool} are
 *             registered directly with {@code AiServices.builder().tools(...)}.
 *             This class is retained for reference only.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallingService {

    private final ToolRegistry toolRegistry;
    private final WeatherTool weatherTool;
    private final DataStatisticsTool dataStatisticsTool;
    private final ExternalApiTool externalApiTool;

    /**
     * Calls the appropriate tool based on the recognized intent.
     *
     * @param intent the recognized intent string
     * @param params a map of parameters to pass to the tool
     * @return the tool's response string
     */
    public String callTool(String intent, Map<String, String> params) {
        log.info("Calling tool for intent: {} with params: {}", intent, params);

        if (params == null) {
            params = Map.of();
        }

        return switch (intent) {
            case IntentRecognizer.WEATHER_QUERY -> {
                String city = params.getOrDefault("city", params.getOrDefault("value", "unknown"));
                yield weatherTool.getWeather(city);
            }
            case IntentRecognizer.DATA_STATISTICS -> {
                String dimension = params.getOrDefault("dimension",
                        params.getOrDefault("value", "general"));
                yield dataStatisticsTool.getStatistics(dimension);
            }
            case IntentRecognizer.EXTERNAL_API -> {
                String url = params.getOrDefault("url", params.get("value"));
                String method = params.getOrDefault("method", "GET");
                if (url == null || url.isBlank()) {
                    yield "Error: URL parameter is required for external API calls.";
                }
                yield externalApiTool.callExternalApi(url, method);
            }
            case IntentRecognizer.KNOWLEDGE_QA ->
                    "Knowledge base search should be handled by the RAG pipeline, not a direct tool call.";
            case IntentRecognizer.GENERAL_CHAT ->
                    "No tool call needed for general chat. The LLM handles this directly.";
            default -> "Unknown intent: " + intent + ". No tool available to handle this request.";
        };
    }

    /**
     * Returns the names of all available tools.
     *
     * @return a list of tool names
     */
    public List<String> getAvailableTools() {
        return toolRegistry.getToolNames();
    }
}
