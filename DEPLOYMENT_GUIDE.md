# Deployment Guide: Crowdsourced Hash Verification System

## Table of Contents
1. [Local Development Setup](#local-development-setup)
2. [Testing the System](#testing-the-system)
3. [Production Deployment](#production-deployment)
4. [Deployment Options](#deployment-options)
5. [Monitoring and Maintenance](#monitoring-and-maintenance)

---

## Local Development Setup

### Prerequisites

Install the following on your local machine:

- **Node.js 18+** and npm: [Download](https://nodejs.org/)
- **Java 17+**: [Download OpenJDK](https://adoptium.net/)
- **Maven 3.8+**: [Download](https://maven.apache.org/download.cgi)
- **PostgreSQL 14+**: [Download](https://www.postgresql.org/download/)
- **Docker** (optional, for integration tests): [Download](https://www.docker.com/get-started)

### Step 1: Clone and Setup Database

```bash
# Create PostgreSQL database
createdb crowdsourced_hash_verification

# Or using psql
psql -U postgres
CREATE DATABASE crowdsourced_hash_verification;
CREATE USER hashverify WITH PASSWORD 'hashverify123';
GRANT ALL PRIVILEGES ON DATABASE crowdsourced_hash_verification TO hashverify;
\q
```

### Step 2: Configure Backend

```bash
cd backend

# Create environment variables file (optional)
cat > .env << EOF
DB_USERNAME=hashverify
DB_PASSWORD=hashverify123
ADMIN_PASSWORD=admin123
ADMIN_API_KEY=dev-admin-key-change-in-production-12345678
EOF

# Install dependencies and build
mvn clean install -DskipTests

# Run database migrations (Flyway will auto-run on startup)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend will start on `http://localhost:8080/api/v1`

### Step 3: Configure Browser Extension

```bash
cd browser-extension

# Install dependencies
npm install

# Build the extension
npm run build:dev

# For development with auto-rebuild
npm run watch
```

### Step 4: Load Extension in Browser

#### Chrome/Edge:
1. Open `chrome://extensions/` (or `edge://extensions/`)
2. Enable "Developer mode" (top right)
3. Click "Load unpacked"
4. Select the `browser-extension/dist` folder

#### Firefox:
1. Open `about:debugging#/runtime/this-firefox`
2. Click "Load Temporary Add-on"
3. Select `browser-extension/manifest.json`

### Step 5: Verify Setup

```bash
# Test backend health
curl http://localhost:8080/api/v1/status

# Expected response:
# {"status":"UP","timestamp":"2026-05-06T..."}
```

---

## Testing the System

### Backend Tests

```bash
cd backend

# Run all tests (unit + property + integration)
mvn test

# Run only unit tests
mvn test -Dtest="*Test"

# Run only property tests
mvn test -Dtest="*Properties"

# Run with coverage (if JaCoCo enabled)
mvn test jacoco:report
```

**Note:** Integration tests require Docker for TestContainers.

### Browser Extension Tests

```bash
cd browser-extension

# Run all tests
npm test

# Run with coverage
npm test:coverage

# Run in watch mode
npm test:watch

# Type checking
npm run type-check

# Linting
npm run lint
```

### Manual End-to-End Test

1. **Start backend**: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
2. **Load extension** in browser (see Step 4 above)
3. **Download a test file** (e.g., any executable or installer)
4. **Right-click the file** → Select "Verify File Integrity"
5. **Check notification** showing verification result

---

## Production Deployment

### Architecture Overview

```
┌─────────────────┐
│ Browser         │
│ Extension       │
└────────┬────────┘
         │ HTTPS
         ↓
┌─────────────────┐      ┌──────────────┐
│ Load Balancer   │──────│ SSL/TLS Cert │
│ (nginx/ALB)     │      └──────────────┘
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌────────┐ ┌────────┐
│Backend │ │Backend │
│Instance│ │Instance│
└───┬────┘ └───┬────┘
    └──────┬───┘
           ↓
    ┌──────────────┐
    │ PostgreSQL   │
    │ (Primary +   │
    │  Replica)    │
    └──────────────┘
```

### Pre-Deployment Checklist

- [ ] Change all default passwords
- [ ] Generate secure admin API key (32+ characters)
- [ ] Configure HTTPS/SSL certificates
- [ ] Set up database backups
- [ ] Configure monitoring and alerting
- [ ] Review security configuration
- [ ] Set up log aggregation
- [ ] Configure rate limiting for production load
- [ ] Test disaster recovery procedures

### Environment Variables (Production)

Create a secure configuration file:

```bash
# Database
DB_USERNAME=hashverify_prod
DB_PASSWORD=<strong-random-password>

# Admin credentials
ADMIN_PASSWORD=<strong-random-password>
ADMIN_API_KEY=<generate-32-char-random-key>

# Security
SPRING_PROFILES_ACTIVE=prod
SERVER_SSL_ENABLED=true
SERVER_SSL_KEY_STORE=/path/to/keystore.p12
SERVER_SSL_KEY_STORE_PASSWORD=<keystore-password>

# Database connection (production)
SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/crowdsourced_hash_verification
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=5

# Logging
LOGGING_LEVEL_COM_HASHVERIFY=INFO
LOGGING_FILE_NAME=/var/log/hashverify/application.log
```

### Backend Deployment Steps

#### Option 1: JAR Deployment (Simple)

```bash
cd backend

# Build production JAR
mvn clean package -DskipTests

# Copy JAR to server
scp target/crowdsourced-hash-verification-1.0.0.jar user@server:/opt/hashverify/

# On server, create systemd service
sudo nano /etc/systemd/system/hashverify.service
```

**systemd service file:**

```ini
[Unit]
Description=Crowdsourced Hash Verification Backend
After=postgresql.service

[Service]
Type=simple
User=hashverify
WorkingDirectory=/opt/hashverify
ExecStart=/usr/bin/java -jar \
  -Xms512m -Xmx2g \
  -Dspring.profiles.active=prod \
  /opt/hashverify/crowdsourced-hash-verification-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

# Environment variables
Environment="DB_USERNAME=hashverify_prod"
Environment="DB_PASSWORD=<password>"
Environment="ADMIN_API_KEY=<api-key>"

[Install]
WantedBy=multi-user.target
```

```bash
# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable hashverify
sudo systemctl start hashverify
sudo systemctl status hashverify

# View logs
sudo journalctl -u hashverify -f
```

#### Option 2: Docker Deployment

```bash
cd backend

# Create Dockerfile
cat > Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR
COPY target/crowdsourced-hash-verification-1.0.0.jar app.jar

# Create non-root user
RUN addgroup -S hashverify && adduser -S hashverify -G hashverify
USER hashverify

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/status || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF

# Build image
docker build -t hashverify-backend:1.0.0 .

# Run container
docker run -d \
  --name hashverify-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USERNAME=hashverify_prod \
  -e DB_PASSWORD=<password> \
  -e ADMIN_API_KEY=<api-key> \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/crowdsourced_hash_verification \
  --restart unless-stopped \
  hashverify-backend:1.0.0
```

#### Option 3: Docker Compose (Backend + Database)

```bash
# Create docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: hashverify-db
    environment:
      POSTGRES_DB: crowdsourced_hash_verification
      POSTGRES_USER: hashverify_prod
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U hashverify_prod"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  backend:
    build: ./backend
    container_name: hashverify-backend
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/crowdsourced_hash_verification
      DB_USERNAME: hashverify_prod
      DB_PASSWORD: ${DB_PASSWORD}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      ADMIN_API_KEY: ${ADMIN_API_KEY}
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "wget", "--spider", "http://localhost:8080/api/v1/status"]
      interval: 30s
      timeout: 10s
      retries: 3
    restart: unless-stopped

volumes:
  postgres-data:
EOF

# Create .env file
cat > .env << 'EOF'
DB_PASSWORD=<strong-random-password>
ADMIN_PASSWORD=<strong-random-password>
ADMIN_API_KEY=<generate-32-char-random-key>
EOF

# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Browser Extension Deployment

#### Build for Production

```bash
cd browser-extension

# Update manifest.json with production backend URL
nano manifest.json
# Change: "https://localhost:8080" → "https://api.yourdomain.com"

# Build production version
npm run build

# Create distribution package
cd dist
zip -r ../hashverify-extension-v1.0.0.zip *
```

#### Chrome Web Store Deployment

1. **Create Developer Account**: [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole)
2. **Pay one-time fee**: $5 USD
3. **Upload extension**:
   - Click "New Item"
   - Upload `hashverify-extension-v1.0.0.zip`
   - Fill in store listing details
   - Add screenshots and description
   - Submit for review (typically 1-3 days)

#### Firefox Add-ons Deployment

1. **Create Developer Account**: [Firefox Add-on Developer Hub](https://addons.mozilla.org/developers/)
2. **Submit Add-on**:
   - Click "Submit a New Add-on"
   - Upload `hashverify-extension-v1.0.0.zip`
   - Choose distribution channel (listed or self-hosted)
   - Fill in listing details
   - Submit for review (typically 1-7 days)

#### Self-Hosted Distribution

```bash
# Host the extension file on your server
scp hashverify-extension-v1.0.0.zip user@server:/var/www/downloads/

# Create update manifest (for auto-updates)
cat > updates.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<gupdate xmlns="http://www.google.com/update2/response" protocol="2.0">
  <app appid="your-extension-id">
    <updatecheck codebase="https://yourdomain.com/downloads/hashverify-extension-v1.0.0.zip" version="1.0.0" />
  </app>
</gupdate>
EOF
```

### Nginx Reverse Proxy Configuration

```nginx
# /etc/nginx/sites-available/hashverify

upstream backend {
    least_conn;
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s;
    # Add more backend instances for load balancing
    # server 127.0.0.1:8081 max_fails=3 fail_timeout=30s;
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name api.yourdomain.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS server
server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    # SSL configuration
    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/m;
    limit_req zone=api_limit burst=20 nodelay;

    # Logging
    access_log /var/log/nginx/hashverify-access.log;
    error_log /var/log/nginx/hashverify-error.log;

    # Proxy settings
    location /api/v1/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Timeouts
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
        
        # CORS for browser extensions
        add_header Access-Control-Allow-Origin "chrome-extension://*" always;
        add_header Access-Control-Allow-Methods "GET, POST, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Content-Type, Authorization" always;
        
        if ($request_method = 'OPTIONS') {
            return 204;
        }
    }

    # Health check endpoint
    location /health {
        access_log off;
        proxy_pass http://backend/api/v1/status;
    }
}
```

```bash
# Enable site and reload nginx
sudo ln -s /etc/nginx/sites-available/hashverify /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## Deployment Options

### Option A: Single Server (Small Scale)

**Best for:** < 1000 users, development/staging

```
Single VPS/VM:
- 2 vCPU, 4GB RAM
- PostgreSQL + Backend on same server
- Cost: ~$20-40/month
```

**Providers:**
- DigitalOcean Droplet
- Linode
- AWS EC2 t3.medium
- Hetzner Cloud

### Option B: Separated Services (Medium Scale)

**Best for:** 1,000 - 10,000 users

```
Application Server:
- 2 vCPU, 4GB RAM
- Backend only
- Cost: ~$20-40/month

Database Server:
- 2 vCPU, 4GB RAM, 50GB SSD
- PostgreSQL with backups
- Cost: ~$30-60/month

Total: ~$50-100/month
```

### Option C: Cloud Platform (Large Scale)

**Best for:** 10,000+ users, high availability

#### AWS Deployment

```
- Elastic Beanstalk (Backend)
- RDS PostgreSQL (Database)
- CloudFront (CDN)
- Route 53 (DNS)
- Certificate Manager (SSL)
- CloudWatch (Monitoring)

Estimated cost: $150-500/month
```

#### Google Cloud Platform

```
- Cloud Run (Backend)
- Cloud SQL (PostgreSQL)
- Cloud CDN
- Cloud Load Balancing
- Cloud Monitoring

Estimated cost: $100-400/month
```

#### Azure Deployment

```
- App Service (Backend)
- Azure Database for PostgreSQL
- Azure CDN
- Application Insights

Estimated cost: $120-450/month
```

### Option D: Kubernetes (Enterprise Scale)

**Best for:** 100,000+ users, multi-region

```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hashverify-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: hashverify-backend
  template:
    metadata:
      labels:
        app: hashverify-backend
    spec:
      containers:
      - name: backend
        image: hashverify-backend:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /api/v1/status
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/v1/status
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: hashverify-backend
spec:
  selector:
    app: hashverify-backend
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
```

---

## Monitoring and Maintenance

### Health Checks

```bash
# Backend health
curl https://api.yourdomain.com/api/v1/status

# Database connection
psql -h db-host -U hashverify_prod -d crowdsourced_hash_verification -c "SELECT 1;"

# Check metrics
curl https://api.yourdomain.com/api/v1/actuator/health
curl https://api.yourdomain.com/api/v1/actuator/metrics
```

### Logging

```bash
# Backend logs (systemd)
sudo journalctl -u hashverify -f

# Backend logs (Docker)
docker logs -f hashverify-backend

# Nginx logs
sudo tail -f /var/log/nginx/hashverify-access.log
sudo tail -f /var/log/nginx/hashverify-error.log

# PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-15-main.log
```

### Database Maintenance

```bash
# Backup database
pg_dump -h localhost -U hashverify_prod crowdsourced_hash_verification > backup-$(date +%Y%m%d).sql

# Restore database
psql -h localhost -U hashverify_prod crowdsourced_hash_verification < backup-20260506.sql

# Vacuum and analyze
psql -h localhost -U hashverify_prod -d crowdsourced_hash_verification -c "VACUUM ANALYZE;"

# Check database size
psql -h localhost -U hashverify_prod -d crowdsourced_hash_verification -c "
SELECT pg_size_pretty(pg_database_size('crowdsourced_hash_verification'));"
```

### Monitoring Tools

**Prometheus + Grafana:**

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'hashverify-backend'
    metrics_path: '/api/v1/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

**Application Performance Monitoring:**
- New Relic
- Datadog
- Elastic APM
- Sentry (for error tracking)

### Automated Backups

```bash
# Create backup script
cat > /opt/hashverify/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/var/backups/hashverify"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

# Database backup
pg_dump -h localhost -U hashverify_prod crowdsourced_hash_verification | \
  gzip > $BACKUP_DIR/db-backup-$DATE.sql.gz

# Keep only last 7 days
find $BACKUP_DIR -name "db-backup-*.sql.gz" -mtime +7 -delete

echo "Backup completed: $DATE"
EOF

chmod +x /opt/hashverify/backup.sh

# Add to crontab (daily at 2 AM)
crontab -e
# Add: 0 2 * * * /opt/hashverify/backup.sh >> /var/log/hashverify-backup.log 2>&1
```

### Security Updates

```bash
# Update system packages
sudo apt update && sudo apt upgrade -y

# Update Java
sudo apt install openjdk-17-jre-headless

# Restart service
sudo systemctl restart hashverify

# Update SSL certificates (Let's Encrypt)
sudo certbot renew
sudo systemctl reload nginx
```

### Performance Tuning

**PostgreSQL tuning:**

```sql
-- /etc/postgresql/15/main/postgresql.conf
shared_buffers = 1GB
effective_cache_size = 3GB
maintenance_work_mem = 256MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 10MB
min_wal_size = 1GB
max_wal_size = 4GB
max_connections = 100
```

**JVM tuning:**

```bash
# Adjust in systemd service or Docker
-Xms1g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/hashverify/
```

---

## Troubleshooting

### Common Issues

**Backend won't start:**
```bash
# Check logs
sudo journalctl -u hashverify -n 100

# Common causes:
# - Database connection failed
# - Port 8080 already in use
# - Missing environment variables
```

**Database connection errors:**
```bash
# Test connection
psql -h localhost -U hashverify_prod -d crowdsourced_hash_verification

# Check PostgreSQL is running
sudo systemctl status postgresql

# Check firewall
sudo ufw status
```

**Extension can't connect to backend:**
```bash
# Check CORS configuration in WebConfig.java
# Check SSL certificate is valid
# Check nginx is proxying correctly
# Check browser console for errors
```

### Support and Documentation

- **GitHub Issues**: Report bugs and feature requests
- **Documentation**: See `/docs` folder
- **Logs**: Check application logs for detailed error messages

---

## Quick Reference

### Local Development
```bash
# Start backend
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Build extension
cd browser-extension && npm run build:dev

# Run tests
mvn test                    # Backend
npm test                    # Extension
```

### Production Deployment
```bash
# Build backend
mvn clean package -DskipTests

# Build extension
npm run build

# Start services
sudo systemctl start hashverify
sudo systemctl start nginx
```

### Monitoring
```bash
# Health check
curl https://api.yourdomain.com/api/v1/status

# View logs
sudo journalctl -u hashverify -f

# Database backup
pg_dump crowdsourced_hash_verification > backup.sql
```

---

**Last Updated:** 2026-05-06  
**Version:** 1.0.0
