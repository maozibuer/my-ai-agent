package com.agent.infrastructure;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight schema migration that runs once at startup.
 *
 * <p>Adds any columns that were introduced after the initial {@code init.sql}
 * without requiring Flyway/Liquibase.  Each migration is idempotent: it checks
 * whether the column already exists before issuing the ALTER TABLE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigration {

    private final DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigrations() {
        addColumnIfNotExists(
                "kb_document",
                "content_hash",
                "VARCHAR(32) DEFAULT NULL COMMENT '文件内容MD5哈希，用于重复上传检测'"
        );
        addIndexIfNotExists(
                "kb_document",
                "idx_content_hash",
                "(`content_hash`, `knowledge_base_id`)"
        );
    }

    /**
     * Adds a column to a table only if it does not already exist.
     */
    private void addColumnIfNotExists(String table, String column, String definition) {
        try (Connection conn = dataSource.getConnection()) {
            // information_schema is available in all MySQL versions
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() " +
                         "AND TABLE_NAME = '" + table + "' " +
                         "AND COLUMN_NAME = '" + column + "'")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    try (Statement alter = conn.createStatement()) {
                        String sql = "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition;
                        alter.execute(sql);
                        log.info("Migration: added column {}.{}", table, column);
                    }
                } else {
                    log.debug("Migration: column {}.{} already exists, skipping", table, column);
                }
            }
        } catch (Exception e) {
            // Log but don't crash startup — the duplicate check will fall back gracefully
            log.error("Migration failed for column {}.{}: {}", table, column, e.getMessage());
        }
    }

    /**
     * Creates an index on a table only if it does not already exist.
     */
    private void addIndexIfNotExists(String table, String indexName, String columns) {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM information_schema.STATISTICS " +
                         "WHERE TABLE_SCHEMA = DATABASE() " +
                         "AND TABLE_NAME = '" + table + "' " +
                         "AND INDEX_NAME = '" + indexName + "'")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    try (Statement create = conn.createStatement()) {
                        String sql = "CREATE INDEX `" + indexName + "` ON `" + table + "` " + columns;
                        create.execute(sql);
                        log.info("Migration: created index {} on {}", indexName, table);
                    }
                } else {
                    log.debug("Migration: index {} on {} already exists, skipping", indexName, table);
                }
            }
        } catch (Exception e) {
            log.warn("Migration: could not create index {} on {}: {}", indexName, table, e.getMessage());
        }
    }
}
