#!/bin/bash

# Crowdsourced Hash Verification System - Local Setup Script
# This script automates the local development environment setup

set -e  # Exit on error

echo "=========================================="
echo "Hash Verification System - Local Setup"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
echo "Checking prerequisites..."

command -v node >/dev/null 2>&1 || { echo -e "${RED}Error: Node.js is not installed${NC}"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo -e "${RED}Error: npm is not installed${NC}"; exit 1; }
command -v java >/dev/null 2>&1 || { echo -e "${RED}Error: Java is not installed${NC}"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo -e "${RED}Error: Maven is not installed${NC}"; exit 1; }
command -v psql >/dev/null 2>&1 || { echo -e "${RED}Error: PostgreSQL is not installed${NC}"; exit 1; }

echo -e "${GREEN}✓ All prerequisites found${NC}"
echo ""

# Check versions
echo "Versions:"
echo "  Node.js: $(node --version)"
echo "  npm: $(npm --version)"
echo "  Java: $(java -version 2>&1 | head -n 1)"
echo "  Maven: $(mvn --version | head -n 1)"
echo "  PostgreSQL: $(psql --version)"
echo ""

# Database setup
echo "=========================================="
echo "Step 1: Database Setup"
echo "=========================================="

read -p "Create PostgreSQL database? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    DB_NAME="crowdsourced_hash_verification"
    DB_USER="hashverify"
    DB_PASS="hashverify123"
    
    echo "Creating database: $DB_NAME"
    
    # Try to create database
    if psql -U postgres -lqt | cut -d \| -f 1 | grep -qw $DB_NAME; then
        echo -e "${YELLOW}Database already exists${NC}"
    else
        createdb -U postgres $DB_NAME || {
            echo -e "${YELLOW}Trying with sudo...${NC}"
            sudo -u postgres createdb $DB_NAME
        }
        echo -e "${GREEN}✓ Database created${NC}"
    fi
    
    # Create user
    psql -U postgres -tc "SELECT 1 FROM pg_user WHERE usename = '$DB_USER'" | grep -q 1 || {
        psql -U postgres -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';" || \
        sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASS';"
        echo -e "${GREEN}✓ User created${NC}"
    }
    
    # Grant privileges
    psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;" || \
    sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
    echo -e "${GREEN}✓ Privileges granted${NC}"
else
    echo "Skipping database setup"
fi
echo ""

# Backend setup
echo "=========================================="
echo "Step 2: Backend Setup"
echo "=========================================="

cd backend

echo "Installing backend dependencies..."
mvn clean install -DskipTests

echo -e "${GREEN}✓ Backend dependencies installed${NC}"
echo ""

# Browser extension setup
echo "=========================================="
echo "Step 3: Browser Extension Setup"
echo "=========================================="

cd ../browser-extension

echo "Installing extension dependencies..."
npm install

echo "Building extension..."
npm run build:dev

echo -e "${GREEN}✓ Browser extension built${NC}"
echo ""

# Summary
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Start the backend:"
echo "   cd backend"
echo "   mvn spring-boot:run -Dspring-boot.run.profiles=dev"
echo ""
echo "2. Load the browser extension:"
echo "   Chrome/Edge:"
echo "     - Open chrome://extensions/"
echo "     - Enable 'Developer mode'"
echo "     - Click 'Load unpacked'"
echo "     - Select: $(pwd)/dist"
echo ""
echo "   Firefox:"
echo "     - Open about:debugging#/runtime/this-firefox"
echo "     - Click 'Load Temporary Add-on'"
echo "     - Select: $(pwd)/manifest.json"
echo ""
echo "3. Test the system:"
echo "   curl http://localhost:8080/api/v1/status"
echo ""
echo "4. Run tests:"
echo "   Backend:  cd backend && mvn test"
echo "   Extension: cd browser-extension && npm test"
echo ""
echo -e "${GREEN}Happy coding!${NC}"
