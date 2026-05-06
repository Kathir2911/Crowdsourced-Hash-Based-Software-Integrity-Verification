# Browser Extension Loading Guide

## Prerequisites
✅ Backend running at `http://localhost:8080`
✅ Extension built (files in `browser-extension/dist/`)
✅ Icons created in `browser-extension/icons/`

## Loading the Extension

### For Chrome/Edge/Brave

1. Open your browser and navigate to:
   - **Chrome**: `chrome://extensions/`
   - **Edge**: `edge://extensions/`
   - **Brave**: `brave://extensions/`

2. Enable **Developer mode** (toggle in top-right corner)

3. Click **Load unpacked**

4. Navigate to and select the folder:
   ```
   ~/Videos/Crowdsourced Hash-Based Software Integrity Verification/browser-extension
   ```

5. The extension should now appear in your extensions list with a green "H" icon

### For Firefox

1. Open Firefox and navigate to: `about:debugging#/runtime/this-firefox`

2. Click **Load Temporary Add-on**

3. Navigate to the `browser-extension` folder and select `manifest.json`

4. The extension will be loaded temporarily (until browser restart)

## Testing the Extension

### Step 1: Check Extension is Loaded
- Click the extension icon in your browser toolbar
- You should see the popup interface

### Step 2: Configure Backend URL (if needed)
The extension is pre-configured to use `http://localhost:8080/api/v1`

### Step 3: Test with a File Download

1. Download any file from the internet (e.g., a small PDF or image)

2. The extension should automatically:
   - Calculate the BLAKE3 hash
   - Submit it to the backend
   - Show a notification with the verification result

3. Check the extension popup to see:
   - Recent verifications
   - Submission statistics
   - System status

### Step 4: Verify Backend Received Submission

```bash
# Check backend logs
cd backend
tail -f logs/hash-verification.log

# Or query the database
psql -U hashverify -d crowdsourced_hash_verification_dev -c "SELECT COUNT(*) FROM hash_submissions;"
```

## Troubleshooting

### Extension Won't Load
- **Error: "Could not load icon"** → Icons are now created, reload the extension
- **Error: "Manifest parsing failed"** → Check `manifest.json` syntax
- **Solution**: Try removing and re-adding the extension

### Extension Loads But Doesn't Work
1. Open browser DevTools (F12)
2. Go to Console tab
3. Look for errors from the extension
4. Check Network tab for failed API calls

### Backend Connection Issues
- Verify backend is running: `curl http://localhost:8080/api/v1/status`
- Check CORS settings in backend (should allow `chrome-extension://` origins)
- Check browser console for CORS errors

### No Notifications Appearing
- Check browser notification permissions
- Click extension icon → Settings → Enable notifications
- Test with a small file download

## Extension Features

### Automatic Hash Verification
- Monitors all file downloads
- Calculates BLAKE3 hash in background
- Submits to backend automatically
- Shows notification with result

### Manual Verification
- Right-click any downloaded file
- Select "Verify Hash" from context menu
- View detailed verification report

### Privacy Features
- No file content uploaded (only hash)
- Domain and filename normalized
- Optional anonymous mode
- Local hash calculation

### Statistics Dashboard
- View in extension popup
- Total verifications performed
- Consensus confidence levels
- Recent verification history

## Next Steps

1. **Test with Multiple Users**: Have others install the extension and download the same file to build consensus

2. **Monitor Consensus**: As more submissions come in, confidence levels will increase

3. **Production Deployment**: When ready, deploy backend to production and update extension's API URL

4. **Publish Extension**: Submit to Chrome Web Store / Firefox Add-ons for public use

## Configuration

### Change Backend URL
Edit `browser-extension/src/config/api.ts`:
```typescript
export const API_BASE_URL = 'https://api.hashverify.com/api/v1';
```

Then rebuild:
```bash
cd browser-extension
npm run build
```

### Adjust Hash Algorithm
Currently using BLAKE3. To change, edit `browser-extension/src/services/BLAKE3Hasher.ts`

### Modify Consensus Thresholds
Edit backend configuration in `backend/src/main/resources/application.yml`
