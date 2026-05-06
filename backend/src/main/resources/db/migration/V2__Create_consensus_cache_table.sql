-- Create consensus_cache table for storing consensus calculation results
CREATE TABLE consensus_cache (
    software_identity_hash VARCHAR(64) PRIMARY KEY,
    source_domain VARCHAR(255) NOT NULL,
    normalized_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    consensus_hash VARCHAR(64),
    confidence DOUBLE PRECISION NOT NULL,
    submission_count INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    lifecycle VARCHAR(32) NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    oldest_submission TIMESTAMP,
    newest_submission TIMESTAMP,
    hash_distribution TEXT
);

-- Create indexes for efficient querying
CREATE INDEX idx_expires ON consensus_cache(expires_at);
CREATE INDEX idx_lifecycle ON consensus_cache(lifecycle);
CREATE INDEX idx_last_updated ON consensus_cache(last_updated);
