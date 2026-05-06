# Development Setup Guide

This guide walks through setting up the development environment for the Crowdsourced Hash Verification System.

## Prerequisites

### Required Software

- **Node.js 18+** and npm
- **Java 17+** (OpenJDK recommended)
- **Maven 3.8+**
- **PostgreSQL 14+**
- **Git**

### Optional Tools

- **Docker** (for containerized PostgreSQL)
- **IntelliJ IDEA** or **VS Code** (recommended IDEs)
- **Postman** or **curl** (for API testing)

## Database Setup

### Option 1: Local PostgreSQL Installation

1. Install PostgreSQL 14+ on your system
2. Create database and user:

```sql
-- Connect as postgres superuser
CREATE DATABASE crowdsourced_hash_verification;
CREATE USER hashverify WITH PASSWORD 'hashverify123';
GRANT ALL PRIVILEGES ON DATABASE crowdsourced_hash_verification TO hashverify;

-- For development
CREATE DATABASE crowdsourced_hash_verification_dev;
GRANT ALL PRIVILEGES ON DATABASE crowdsourced_hash_verification_dev TO hashverify;
```

### Option 2: Docker PostgreSQL

```bash
# Run PostgreSQL in Docker
docker run --name hash-verify-postgres \
  -e POSTGRES_DB=crowdsourced_hash_verification \
  -e POSTGRES_USER=hashverify \
  -e POSTGRES_PASSWORD=hashverify123 \
  -p 5432:5432 \
  -d postgres:14

# Create development database
docker exec -it hash-verify-postgres createdb -U hashverify crowdsourced_hash_verification_dev
```

## Backend Setup

1. Navigate to backend directory:
```bash
cd backend
```

2. Install dependencies and run tests:
```bash
mvn clean install
```

3. Run the application:
```bash
# Development mode
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Or with environment variables
DB_USERNAME=hashverify DB_PASSWORD=hashverify123 mvn spring-boot:run
```

4. Verify backend is running:
```bash
curl http://localhost:8080/api/v1/status
```

## Browser Extension Setup

1. Navigate to browser extension directory:
```bash
cd browser-extension
```

2. Install dependencies:
```bash
npm install
```

3. Build the extension:
```bash
# Development build
npm run build:dev

# Production build
npm run build

# Watch mode for development
npm run watch
```

4. Run tests:
```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Generate coverage report
npm run test:coverage
```

## Loading Extension in Browser

### Chrome/Chromium

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable "Developer mode" (toggle in top right)
3. Click "Load unpacked"
4. Select the `browser-extension` directory
5. The extension should appear in your extensions list

### Firefox

1. Open Firefox and navigate to `about:debugging`
2. Click "This Firefox"
3. Click "Load Temporary Add-on"
4. Navigate to `browser-extension` directory and select `manifest.json`

## Development Workflow

### Backend Development

1. Make code changes in `backend/src/main/java/`
2. Run tests: `mvn test`
3. Start application: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
4. Test API endpoints with Postman or curl

### Frontend Development

1. Make code changes in `browser-extension/src/`
2. Run tests: `npm test`
3. Build extension: `npm run build:dev`
4. Reload extension in browser (click reload button in extensions page)
5. Test functionality through browser extension popup

### Database Changes

1. Create new migration file in `backend/src/main/resources/db/migration/`
2. Follow naming convention: `V{version}__{description}.sql`
3. Restart application to apply migrations
4. Verify changes in database

## Testing

### Unit Tests

```bash
# Backend unit tests
cd backend
mvn test

# Frontend unit tests
cd browser-extension
npm test
```

### Integration Tests

```bash
# Backend integration tests (uses TestContainers)
cd backend
mvn test -Dtest="*IntegrationTest"

# End-to-end testing
# 1. Start backend: mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 2. Load extension in browser
# 3. Test file verification workflow
```

### Property-Based Tests

```bash
# Backend property tests (jqwik)
cd backend
mvn test -Dtest="*Properties"

# Frontend property tests (fast-check)
cd browser-extension
npm test -- --testNamePattern="Property"
```

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.yml`:

- Database connection settings
- API rate limiting
- Consensus thresholds
- Security settings

### Extension Configuration

The extension loads configuration from Chrome storage. Default settings are in `browser-extension/src/background.ts`.

## Troubleshooting

### Common Issues

1. **Database connection failed**
   - Verify PostgreSQL is running
   - Check connection credentials
   - Ensure database exists

2. **Extension not loading**
   - Check browser console for errors
   - Verify manifest.json syntax
   - Ensure all required files are built

3. **CORS errors**
   - Verify backend CORS configuration
   - Check extension permissions in manifest.json

4. **Tests failing**
   - Ensure test database is configured
   - Check for port conflicts
   - Verify all dependencies are installed

### Logs

- **Backend logs**: `backend/logs/hash-verification.log`
- **Browser console**: F12 → Console tab
- **Extension logs**: F12 → Extensions tab → Inspect views

## Next Steps

After setup is complete:

1. Review the [API Documentation](API.md)
2. Read the [Architecture Overview](ARCHITECTURE.md)
3. Check the [Testing Guide](TESTING.md)
4. Start implementing features from the task list