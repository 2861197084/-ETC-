USE etc;

CREATE TABLE IF NOT EXISTS pass_record_0 (
  id BIGINT NOT NULL AUTO_INCREMENT,
  gcxh VARCHAR(64) NOT NULL,
  xzqhmc VARCHAR(128) NOT NULL,
  kkmc VARCHAR(128) NOT NULL,
  fxlx VARCHAR(32) NOT NULL,
  gcsj DATETIME NOT NULL,
  hpzl VARCHAR(32) NOT NULL,
  hp VARCHAR(32) NOT NULL,
  clppxh VARCHAR(128) NOT NULL,
  plate_hash INT NOT NULL,
  checkpoint_id VARCHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gcxh (gcxh),
  KEY idx_gcsj (gcsj),
  KEY idx_hp (hp),
  KEY idx_checkpoint_id_gcsj (checkpoint_id, gcsj)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pass_record_1 LIKE pass_record_0;

CREATE TABLE IF NOT EXISTS checkpoint_dim (
  checkpoint_id VARCHAR(64) NOT NULL,
  checkpoint_name VARCHAR(128) NOT NULL,
  xzqhmc VARCHAR(128) NOT NULL,
  PRIMARY KEY (checkpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agg_checkpoint_minute (
  checkpoint_id VARCHAR(64) NOT NULL,
  minute_ts DATETIME NOT NULL,
  flow BIGINT NOT NULL,
  PRIMARY KEY (checkpoint_id, minute_ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
