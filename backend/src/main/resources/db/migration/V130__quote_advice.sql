CREATE TABLE crm_quote_advice (
  customer_id BIGINT NOT NULL PRIMARY KEY,
  project_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  generation BIGINT NOT NULL DEFAULT 1,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  suggestions JSON NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_quote_advice_claim(status,updated_at),
  KEY idx_quote_advice_scope(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
