-- ============================================================
-- 智能问答系统 - MySQL 数据库初始化脚本
-- 数据库名: ai_agent
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- 
-- 用法:
--   全新安装: mysql -u root -p < init.sql
--   旧库升级: 取消下方 "旧库升级" 区块的注释后执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS ai_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_agent;

-- ============================================================
-- 1. 系统用户表 (sys_user)
-- ============================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名，唯一',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码，BCrypt加密存储',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱地址',
    `avatar`      VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    `role`        VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN-管理员，USER-普通用户',
    `creator`     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    `tenant_id`   BIGINT       DEFAULT 0 COMMENT '租户ID，用于多租户隔离',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 默认管理员（密码: admin123，BCrypt加密）
INSERT INTO `sys_user` (`username`, `password`, `email`, `role`, `creator`)
VALUES ('admin', '$2a$12$TqEDJPV.NZz7bb2YomOw0.4aXVLsG3BAjGfISbbshkswxJUtOBmw6', 'admin@ai-agent.com', 'ADMIN', 'system');

-- ============================================================
-- 2. 聊天记录表 (chat_record)
-- ============================================================
DROP TABLE IF EXISTS `chat_record`;
CREATE TABLE `chat_record` (
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`           BIGINT      NOT NULL COMMENT '用户ID，关联sys_user.id',
    `session_id`        VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_message`      TEXT        DEFAULT NULL COMMENT '用户消息内容',
    `assistant_message` TEXT        DEFAULT NULL COMMENT 'AI助手回复内容',
    `creator`           VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `tenant_id`         BIGINT      DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录表';

-- ============================================================
-- 3. 知识库基础信息表 (kb_base)
-- ============================================================
DROP TABLE IF EXISTS `kb_base`;
CREATE TABLE `kb_base` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`           VARCHAR(128) NOT NULL COMMENT '知识库名称',
    `description`    TEXT         DEFAULT NULL COMMENT '知识库描述',
    `department`     VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
    `document_count` INT          NOT NULL DEFAULT 0 COMMENT '文档数量',
    `creator`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `tenant_id`      BIGINT       DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`),
    KEY `idx_department` (`department`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库基础信息表';

-- ============================================================
-- 4. 知识库文档表 (kb_document)
-- 已合并: knowledge_base_id DEFAULT 0 + content_hash 列 + 索引
-- ============================================================
DROP TABLE IF EXISTS `kb_document`;
CREATE TABLE `kb_document` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_name`         VARCHAR(256) NOT NULL COMMENT '文件名',
    `file_type`         VARCHAR(32)  NOT NULL COMMENT '文件类型：PDF, DOCX, TXT, MD等',
    `knowledge_base_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '所属知识库ID，0表示通用知识库',
    `chunk_count`       INT          NOT NULL DEFAULT 0 COMMENT '文本分块数量',
    `file_size`         BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `content_hash`      VARCHAR(32)  DEFAULT NULL COMMENT '文件内容MD5哈希，用于重复上传检测',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `creator`           VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `tenant_id`         BIGINT       DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_base_id` (`knowledge_base_id`),
    KEY `idx_status` (`status`),
    KEY `idx_file_type` (`file_type`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_content_hash` (`content_hash`, `knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ============================================================
-- 5. 系统操作日志表 (sys_log)
-- ============================================================
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户ID',
    `operation`   VARCHAR(128) NOT NULL COMMENT '操作描述',
    `method`      VARCHAR(256) DEFAULT NULL COMMENT '请求方法（类名#方法名）',
    `params`      TEXT         DEFAULT NULL COMMENT '请求参数（JSON格式）',
    `ip`          VARCHAR(64)  DEFAULT NULL COMMENT '请求IP地址',
    `time`        BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    `creator`     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `tenant_id`   BIGINT       DEFAULT 0 COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_operation` (`operation`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';


-- ============================================================
-- 旧库升级（仅对已有数据库执行，取消注释后运行）
-- ============================================================

-- -- 修复: knowledge_base_id 缺少默认值
-- ALTER TABLE `kb_document`
--     MODIFY COLUMN `knowledge_base_id` BIGINT NOT NULL DEFAULT 0
--     COMMENT '所属知识库ID，0表示通用知识库，关联kb_base.id';

-- -- 新增: content_hash 列 + 索引（重复文件检测）
-- ALTER TABLE `kb_document`
--     ADD COLUMN `content_hash` VARCHAR(32) DEFAULT NULL
--         COMMENT '文件内容MD5哈希，用于重复上传检测';

-- CREATE INDEX `idx_content_hash` ON `kb_document` (`content_hash`, `knowledge_base_id`);