package com.agent.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.common.Result;
import com.agent.tool.service.ToolRegistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for tool management endpoints.
 * Provides information about available tools in the system.
 */
@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Tag(name = "Tool API", description = "Endpoints for tool information and management")
public class ToolController {

    private final ToolRegistry toolRegistry;

    /**
     * Returns the list of available tools.
     *
     * @return a Result containing a list of tool names
     */
    @GetMapping
    @Operation(summary = "Get available tools", description = "Returns the list of available tool names")
    public Result<List<String>> getAvailableTools() {
        log.info("Request for available tools");
        List<String> tools = toolRegistry.getToolNames();
        return Result.success(tools);
    }
}
