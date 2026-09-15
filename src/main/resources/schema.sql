-- ============================================================
-- kb-agent-lite 内嵌 H2 建表脚本（MODE=MySQL）
-- 全部 IF NOT EXISTS，重复执行安全
-- ============================================================

-- ----------------------------
-- 知识库分类表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_category (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id     BIGINT       NOT NULL DEFAULT 0,
    category_name VARCHAR(100) NOT NULL,
    category_code VARCHAR(50)  NOT NULL,
    description   VARCHAR(500),
    sort_order    INT          NOT NULL DEFAULT 0,
    level         INT          NOT NULL DEFAULT 1,
    path          VARCHAR(500),
    status        TINYINT      NOT NULL DEFAULT 1,
    document_count INT         NOT NULL DEFAULT 0,
    create_user   VARCHAR(64),
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_user   VARCHAR(64),
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_category_parent ON knowledge_category (parent_id);

-- ----------------------------
-- 知识库文档表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_document (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id      VARCHAR(64)  NOT NULL,
    title            VARCHAR(500) NOT NULL DEFAULT '',
    original_file_name VARCHAR(255) NOT NULL,
    file_type        VARCHAR(100) NOT NULL,
    file_size        BIGINT       NOT NULL DEFAULT 0,
    file_path        VARCHAR(500) NOT NULL,
    category_id      VARCHAR(64),
    category_name    VARCHAR(100) NOT NULL DEFAULT 'KNOWLEDGE',
    file_extension   VARCHAR(20)  NOT NULL,
    content_length   INT          NOT NULL DEFAULT 0,
    chunk_count      INT          NOT NULL DEFAULT 0,
    vector_count     INT          NOT NULL DEFAULT 0,
    embedding_model  VARCHAR(100),
    vector_dimension INT,
    vector_status    TINYINT      NOT NULL DEFAULT 0,
    vector_error     TEXT,
    metadata_json    TEXT,
    status           TINYINT      NOT NULL DEFAULT 1,
    remark           VARCHAR(500),
    tags             VARCHAR(500),
    create_user      VARCHAR(64),
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_user      VARCHAR(64),
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id          VARCHAR(64)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_document_id ON knowledge_document (document_id);
CREATE INDEX IF NOT EXISTS idx_document_category ON knowledge_document (category_id);
CREATE INDEX IF NOT EXISTS idx_document_status ON knowledge_document (status);

-- ----------------------------
-- 智能体会话表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_session (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   VARCHAR(64) NOT NULL,
    user_id      BIGINT      NOT NULL DEFAULT 0,
    title        VARCHAR(255) NOT NULL DEFAULT '新会话',
    title_source TINYINT     NOT NULL DEFAULT 0,
    status       TINYINT     NOT NULL DEFAULT 1,
    token_count  INT         NOT NULL DEFAULT 0,
    model_type   VARCHAR(64) NOT NULL DEFAULT '',
    is_deleted   TINYINT     NOT NULL DEFAULT 0,
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_session_id ON agent_session (session_id);

-- ----------------------------
-- 智能体消息历史表（多轮对话记忆持久化）
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_message (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL,
    user_id       BIGINT      NOT NULL DEFAULT 0,
    role          VARCHAR(16) NOT NULL DEFAULT 'USER',
    content       CLOB,
    content_type  VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    token_count   INT         NOT NULL DEFAULT 0,
    metadata_json TEXT,
    is_deleted    TINYINT     NOT NULL DEFAULT 0,
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_message_session ON agent_message (session_id);

-- ----------------------------
-- 初始数据：默认顶级分类
-- ----------------------------
INSERT INTO knowledge_category (parent_id, category_name, category_code, description, sort_order, level, path, status, document_count, create_user)
SELECT 0, '默认分类', 'DEFAULT', '系统默认知识库分类', 0, 1, '0', 1, 0, 'admin'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_category WHERE category_code = 'DEFAULT');

-- 升级兼容：已存在的旧库放宽 file_type 长度（OOXML MIME 串 71 字符）
ALTER TABLE knowledge_document ALTER COLUMN file_type VARCHAR(100);
