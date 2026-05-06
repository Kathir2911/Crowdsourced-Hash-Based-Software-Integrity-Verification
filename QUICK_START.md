# Quick Start Guide

## System Status ✅

Your Crowdsourced Hash Verification system is **ready to use**!

### Backend Status
- ✅ Running at `http://localhost:8080`
- ✅ Database connected (PostgreSQL)
- ✅ 3 test submissions stored
- ✅ Consensus calculation working
- ✅ All endpoints operational

### Browser Extension Status
- ✅ Built and compiled
- ✅ Icons created
- ✅ Ready to load in browser

## Load the Extension NOW

### Quick Steps (Chrome/Edge/Brave)

1. Open: `chrome://extensions/` (or `edge://extensions/`)
2. Enable **Developer mode** (top-right toggle)
3. Click **Load unpacked**
4. Select folder: `browser-extension`
5. Done! Look for the green "H" icon

**Full instructions**: See `BROWSER_EXTENSION_GUIDE.md`

## Test the System

### Test 1: Download a File
1. Download any file from the internet
2. Extension will automatically verify it
3. Check notification for result

### Test 2: Check Backend
```bash
curl http://localhost:8080/api/v1/status
```

### Test 3: View Submissions
```bash
psql -U hashverify -d crowdsourced_hash_verification_dev -c "SELECT * FROM hash_submissions LIMIT 5;"
```

## Understanding the Results

### Verification Statuses

- **NO_CONSENSUS**: First submission for this file (0-2 submissions)
- **SUSPICIOUS_LOW_CONFIDENCE**: Some consensus but weak (3-9 submissions)
- **ESTABLISHED**: Strong consensus (10+ submissions, >80% agreement)
- **TAMPERED**: Hash doesn't match consensus
- **SUSPICIOUS_DIVERGENT**: Multiple competing hashes

### Confidence Levels

- **100% with 3 submissions**: All 3 match, but sample size is small
- **100% with 10+ submissions**: Strong consensus, file is likely legitimate
- **<80% confidence**: Conflicting hashes, investigate further

## Current Test Data

You have 3 test submissions in the database:
- **File**: `test.exe` from `example.com` (1024 bytes)
- **Hash**: `abc123def456...` (64 chars)
- **Consensus**: 100% confidence (all 3 match)
- **Status**: `SUSPICIOUS_LOW_CONFIDENCE` (needs 10+ for ESTABLISHED)

## What Happens Next?

### As You Use the Extension

1. **Download files** → Extension calculates hash → Submits to backend
2. **More users submit** → Consensus strengthens → Confidence increases
3. **Tampered files detected** → Hash mismatch → Warning shown

### Building Consensus

- First 3 submissions: System learns the "correct" hash
- 10+ submissions: Consensus becomes established
- Conflicting hashes: System flags as suspicious

## Key Files

- `DEPLOYMENT_GUIDE.md` - Full deployment instructions
- `API_TESTING_GUIDE.md` - API endpoint testing
- `BROWSER_EXTENSION_GUIDE.md` - Extension loading and usage
- `SETUP_SUMMARY.md` - System architecture overview

## Troubleshooting

### Extension Won't Load
→ See `BROWSER_EXTENSION_GUIDE.md` troubleshooting section

### Backend Not Responding
```bash
cd backend
./mvnw spring-boot:run
```

### Database Connection Issues
```bash
sudo systemctl status postgresql
psql -U hashverify -d crowdsourced_hash_verification_dev
```

## Production Deployment

When ready to deploy to production:

1. **Backend**: Deploy to cloud (AWS, GCP, Azure)
2. **Database**: Use managed PostgreSQL service
3. **Extension**: Update API URL and rebuild
4. **Publish**: Submit to Chrome Web Store / Firefox Add-ons

See `DEPLOYMENT_GUIDE.md` for detailed production deployment steps.

## Support

- Check logs: `backend/logs/hash-verification.log`
- View database: `psql -U hashverify -d crowdsourced_hash_verification_dev`
- Test API: `curl http://localhost:8080/api/v1/status`

---

**You're all set! Load the extension and start verifying files.** 🚀
