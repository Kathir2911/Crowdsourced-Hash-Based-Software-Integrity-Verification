-- Create hash_submissions table
CREATE TABLE hash_submissions (
    id UUID PRIMARY KEY,
    software_identity_hash VARCHAR(64) NOT NULL,
    source_domain VARCHAR(255) NOT NULL,
    normalized_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    hash VARCHAR(64) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    client_version VARCHAR(32),
    replay_protection_hash VARCHAR(64) NOT NULL,
    user_agent TEXT
);

-- Create indexes for performance
CREATE INDEX idx_software_identity_hash ON hash_submissions(software_identity_hash);
CREATE INDEX idx_hash ON hash_submissions(hash);
CREATE INDEX idx_timestamp ON hash_submissions(timestamp);
CREATE INDEX idx_replay_protection ON hash_submissions(replay_protection_hash, software_identity_hash, timestamp);