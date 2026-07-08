package com.agent.tool.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for recognizing user intent based on keyword matching.
 * Determines which tool or capability should handle a given user input.
 *
 * @deprecated Since the migration to LangChain4j AI Services, intent recognition
 *             is handled automatically by the LLM via function calling
 *             (see {@code @Tool} annotations on {@code WeatherTool},
 *             {@code DataStatisticsTool}, {@code ExternalApiTool}).
 *             This class is retained for reference only.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRecognizer {

    // Intent constants
    public static final String WEATHER_QUERY = "WEATHER_QUERY";
    public static final String DATA_STATISTICS = "DATA_STATISTICS";
    public static final String EXTERNAL_API = "EXTERNAL_API";
    public static final String KNOWLEDGE_QA = "KNOWLEDGE_QA";
    public static final String GENERAL_CHAT = "GENERAL_CHAT";

    // Keyword mappings
    private static final List<String> WEATHER_KEYWORDS = List.of(
            "天气", "weather", "气温", "温度", "下雨", "晴", "阴", "暴雨", "台风");
    private static final List<String> STATISTICS_KEYWORDS = List.of(
            "统计", "数据", "statistics", "用户数", "订单", "收入", "营收", "报表", "指标", "metric");
    private static final List<String> API_KEYWORDS = List.of(
            "调用", "接口", "api", "http", "请求", "request", "外部");
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "文档", "知识库", "知识", "资料", "查一下", "什么是", "解释", "说明", "文档库");

    /**
     * Recognizes the intent of a user's input based on keyword matching.
     *
     * @param userInput the user's input text
     * @return one of: WEATHER_QUERY, DATA_STATISTICS, EXTERNAL_API, KNOWLEDGE_QA, GENERAL_CHAT
     */
    public String recognizeIntent(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return GENERAL_CHAT;
        }

        String lowerInput = userInput.toLowerCase();

        log.debug("Recognizing intent for input: {}", userInput);

        // Check weather keywords
        for (String keyword : WEATHER_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                log.info("Intent recognized: {} (matched keyword: {})", WEATHER_QUERY, keyword);
                return WEATHER_QUERY;
            }
        }

        // Check statistics keywords
        for (String keyword : STATISTICS_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                log.info("Intent recognized: {} (matched keyword: {})", DATA_STATISTICS, keyword);
                return DATA_STATISTICS;
            }
        }

        // Check external API keywords
        for (String keyword : API_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                log.info("Intent recognized: {} (matched keyword: {})", EXTERNAL_API, keyword);
                return EXTERNAL_API;
            }
        }

        // Check knowledge base keywords
        for (String keyword : KNOWLEDGE_KEYWORDS) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                log.info("Intent recognized: {} (matched keyword: {})", KNOWLEDGE_QA, keyword);
                return KNOWLEDGE_QA;
            }
        }

        // Default to general chat
        log.info("Intent recognized: {}", GENERAL_CHAT);
        return GENERAL_CHAT;
    }
}
