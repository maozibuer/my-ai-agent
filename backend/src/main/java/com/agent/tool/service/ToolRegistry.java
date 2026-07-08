package com.agent.tool.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry for discovering and managing available tools.
 * Scans the Spring ApplicationContext for beans in the tool package
 * and provides them as a list for the ReAct agent to use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final ApplicationContext applicationContext;

    /** Base package to scan for tool components */
    private static final String TOOL_PACKAGE = "com.agent.tool.tool";

    /** Cached list of tool beans */
    private List<Object> toolCache;

    /**
     * Returns all tool beans found in the tool package.
     * Results are cached after the first lookup.
     *
     * @return a list of tool component instances
     */
    public List<Object> getAllTools() {
        if (toolCache != null) {
            return toolCache;
        }

        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(
                org.springframework.stereotype.Component.class);

        List<Object> tools = new ArrayList<>();
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            String beanClassName = bean.getClass().getName();

            // Check if the bean is in the tool package
            if (beanClassName.startsWith(TOOL_PACKAGE)) {
                tools.add(bean);
                log.debug("Registered tool: {} ({})", entry.getKey(), beanClassName);
            }
        }

        toolCache = tools;
        log.info("Discovered {} tools in package {}", tools.size(), TOOL_PACKAGE);
        return tools;
    }

    /**
     * Returns the simple class names of all registered tools.
     *
     * @return a list of tool names
     */
    public List<String> getToolNames() {
        List<Object> tools = getAllTools();
        List<String> names = new ArrayList<>();

        for (Object tool : tools) {
            // Get the simple class name, stripping any CGLIB proxy suffixes
            String className = tool.getClass().getSimpleName();
            className = className.replaceAll("$$.*$", "");
            names.add(className);
        }

        return names;
    }
}
