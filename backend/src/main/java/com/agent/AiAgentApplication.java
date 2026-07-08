package com.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Enterprise-level Intelligent Q&A Agent System.
 * <p>
 * Enables:
 * <ul>
 *   <li>Spring Boot auto-configuration</li>
 *   <li>MyBatis-Plus mapper scanning across all packages</li>
 *   <li>Scheduled task execution</li>
 *   <li>Asynchronous method execution</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.agent.**.mapper")
@EnableScheduling
@EnableAsync
public class AiAgentApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
