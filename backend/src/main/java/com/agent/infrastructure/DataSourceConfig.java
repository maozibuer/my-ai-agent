package com.agent.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Data source and MyBatis-Plus mapper scanning configuration.
 * Most data source settings are handled by application.yml via
 * Spring Boot auto-configuration.
 */
@Configuration
@MapperScan("com.agent.*.mapper")
public class DataSourceConfig {
    // Minimal configuration - data source properties are managed in application.yml
}
