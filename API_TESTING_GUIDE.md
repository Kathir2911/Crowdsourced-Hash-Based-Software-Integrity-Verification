# API Testing Guide

Quick reference for testing the Crowdsourced Hash Verification API locally.

## Prerequisites

- Backend running: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- Backend URL: `http://localhost:8080/api/v1`

## API Endpoints

### 1. Health Check

```bash
curl http://localhost:8080/api/v1/status
```

**Expected Response:**
```json
{
  "service": "Crowdsourced Hash Verification",
  "version": "1.0.0",
  "status": "healthy",
  "timestamp": "2026-05-07T00:17:03.492229075"
}
```

---

### 2. Submit a Hash

**Endpoint:** `POST /api/v1/submissions`

**Complete Request:**
```bash
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {
      "sourceDomain": "example.com",
      "normalizedFilename": "test.exe",
      "fileSize": 1024
    },
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1",
    "clientVersion": "1.0.0",
    "replayProtectionHash": "replay123456789012345678901234567890123456789012345678901234"
  }'
```

**Required Fields:**
- `softwareIdentity.sourceDomain`: Domain where file was downloaded
- `softwareIdentity.normalizedFilename`: Normalized filename
- `softwareIdentity.fileSize`: File size in bytes
- `hash`: 64-character hexadecimal BLAKE3 hash
- `replayProtectionHash`: 64-character replay protection hash (prevents duplicate submissions)
- `clientVersion`: Browser extension version (optional)

**Expected Response:**
```json
{
  "message": "Hash submission received successfully",
  "submissionId": "550e8400-e29b-41d4-a716-446655440000",
  "identityHash": "def789...",
  "timestamp": "2026-05-07T00:20:00.123456789"
}
```

---

### 3. Verify a Hash

**Endpoint:** `POST /api/v1/verify`

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/verify \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {
      "sourceDomain": "example.com",
      "normalizedFilename": "test.exe",
      "fileSize": 1024
    },
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
  }'
```

**Expected Response (No Consensus Yet):**
```json
{
  "status": "UNKNOWN",
  "confidence": 0.0,
  "submissionCount": 0,
  "consensusHash": null,
  "message": "No consensus data available for this software",
  "timestamp": "2026-05-07T00:20:00.123456789",
  "suspicionLevel": "NONE",
  "recommendedAction": "This file has not been verified by the community yet"
}
```

**Expected Response (With Consensus):**
```json
{
  "status": "VERIFIED",
  "confidence": 0.85,
  "submissionCount": 10,
  "consensusHash": "abc123def456...",
  "message": "Hash matches community consensus",
  "timestamp": "2026-05-07T00:20:00.123456789",
  "suspicionLevel": "NONE",
  "recommendedAction": "File appears to be legitimate"
}
```

---

## Complete Test Workflow

### Step 1: Submit multiple hashes for the same file

```bash
# Submission 1
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1",
    "clientVersion": "1.0.0",
    "replayProtectionHash": "replay111111111111111111111111111111111111111111111111111111111111"
  }'

# Submission 2 (same hash, different replay protection)
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1",
    "clientVersion": "1.0.0",
    "replayProtectionHash": "replay222222222222222222222222222222222222222222222222222222222222"
  }'

# Submission 3 (same hash, different replay protection)
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1",
    "clientVersion": "1.0.0",
    "replayProtectionHash": "replay333333333333333333333333333333333333333333333333333333333333"
  }'
```

### Step 2: Verify the hash

```bash
curl -X POST http://localhost:8080/api/v1/verify \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
  }'
```

**Expected:** Status should be `ESTABLISHED` with confidence ~1.0 (100%) since all 3 submissions match.

### Step 3: Test tamper detection

```bash
# Submit a different hash for the same file
curl -X POST http://localhost:8080/api/v1/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "different456789different456789different456789different456789different",
    "clientVersion": "1.0.0",
    "replayProtectionHash": "replay444444444444444444444444444444444444444444444444444444444444"
  }'

# Verify with the different hash
curl -X POST http://localhost:8080/api/v1/verify \
  -H "Content-Type: application/json" \
  -d '{
    "softwareIdentity": {"sourceDomain": "example.com", "normalizedFilename": "test.exe", "fileSize": 1024},
    "hash": "different456789different456789different456789different456789different"
  }'
```

**Expected:** Status should be `TAMPERED` or `SUSPICIOUS_LOW_CONFIDENCE` since this hash doesn't match the consensus.

---

## Common Errors

### Error: "Replay protection hash cannot be blank"
**Solution:** Add the `replayProtectionHash` field to your submission request.

### Error: "Could not commit JPA transaction"
**Solution:** Check that PostgreSQL is running and the database exists:
```bash
sudo systemctl status postgresql
psql -U hashverify -d crowdsourced_hash_verification_dev -c "SELECT 1;"
```

### Error: HTTP 403 Forbidden
**Solution:** Security configuration blocking request. Check SecurityConfig.java allows the endpoint.

### Error: HTTP 404 Not Found
**Solution:** Check the URL path. Remember the context path is `/api/v1`.

---

## Admin Endpoints (Requires Authentication)

Admin endpoints require API key authentication via the `X-API-Key` header.

### Get System Statistics

```bash
curl -X GET http://localhost:8080/api/v1/admin/stats \
  -H "X-API-Key: dev-admin-key-change-in-production-12345678"
```

### Get Configuration

```bash
curl -X GET http://localhost:8080/api/v1/admin/config \
  -H "X-API-Key: dev-admin-key-change-in-production-12345678"
```

### Cleanup Expired Data

```bash
curl -X POST http://localhost:8080/api/v1/admin/cleanup \
  -H "X-API-Key: dev-admin-key-change-in-production-12345678"
```

---

## Database Queries (For Debugging)

```bash
# Connect to database
psql -U hashverify -d crowdsourced_hash_verification_dev

# View all submissions
SELECT * FROM hash_submissions;

# View consensus cache
SELECT * FROM consensus_cache;

# Count submissions by software identity
SELECT software_identity_hash, COUNT(*) 
FROM hash_submissions 
GROUP BY software_identity_hash;
```

---

## Notes

- **Replay Protection Hash**: In production, this is generated from IP + User Agent + Time Window to prevent duplicate submissions. For testing, use any unique 64-character hex string.
- **Minimum Submissions**: Default is 3 submissions before consensus is established (configurable in application.yml).
- **Consensus Threshold**: Default is 70% agreement (configurable in application.yml).
- **Hash Format**: Must be exactly 64 hexadecimal characters (BLAKE3 hash output).

---

**Last Updated:** 2026-05-07
