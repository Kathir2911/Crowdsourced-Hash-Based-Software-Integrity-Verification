# Crowdsourced Hash-Based Software Integrity Verification System

A distributed security framework that detects tampered software binaries through cryptographic hashing and consensus mechanisms.

## Architecture

- **Browser Extension** (`browser-extension/`): TypeScript client for file selection and hash computation
- **Backend Server** (`backend/`): Java Spring Boot server for hash registry and verification
- **Database**: PostgreSQL for storing hash submissions and consensus data

## Quick Start

### Prerequisites

- Node.js 18+ and npm
- Java 17+
- Maven 3.8+
- PostgreSQL 14+

### Browser Extension Development

```bash
cd browser-extension
npm install
npm run build
npm run test
```

### Backend Server Development

```bash
cd backend
mvn clean install
mvn spring-boot:run
mvn test
```

### Database Setup

```bash
# Create database
createdb crowdsourced_hash_verification

# Run migrations (handled by Spring Boot)
cd backend
mvn spring-boot:run
```

## Project Structure

```
├── browser-extension/          # TypeScript browser extension
│   ├── src/                   # Source code
│   ├── tests/                 # Jest unit and property tests
│   ├── manifest.json          # Extension manifest
│   └── package.json           # Dependencies and scripts
├── backend/                   # Java Spring Boot backend
│   ├── src/main/java/         # Java source code
│   ├── src/test/java/         # JUnit and jqwik tests
│   ├── src/main/resources/    # Configuration and SQL
│   └── pom.xml                # Maven configuration
└── docs/                      # Documentation
```

## Testing

The system uses dual testing approach:
- **Unit Tests**: Specific examples and edge cases
- **Property Tests**: Universal properties across all inputs

### Browser Extension Testing
- Jest for unit tests
- fast-check for property-based tests

### Backend Testing
- JUnit 5 for unit tests
- jqwik for property-based tests
- TestContainers for integration tests

## Security

- BLAKE3 cryptographic hashing
- HTTPS-only communication
- Rate limiting and replay protection
- Privacy-preserving data transmission

## License

MIT License - see LICENSE file for details