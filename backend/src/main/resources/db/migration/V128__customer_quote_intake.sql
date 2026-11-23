-- Independent of referral attribution and contracts. A customer becomes a quote
-- prospect when their referrer explicitly creates this row.
CREATE TABLE crm_quote_intake (
    customer_id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    promoter_id BIGINT NOT NULL,
    token CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at DATETIME NULL,
    submission_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    requirements_json JSON NULL,
    UNIQUE KEY uk_quote_intake_token (token),
    KEY idx_quote_intake_project (tenant_id, project_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
