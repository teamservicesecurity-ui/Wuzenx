#!/bin/bash
# ============================================================
# FILE: deploy/entrypoint.sh
# ============================================================
set -e

echo "=== CyberAI-AOP C2 Server Starting ==="
echo "Node version: $(node --version)"
echo "NODE_ENV: $NODE_ENV"
echo "Port: $PORT"

# Ensure data directory exists
mkdir -p /app/data
mkdir -p /app/logs

# Run database migrations if needed
if [ -f /app/server/src/db/migrate.js ]; then
    echo "Running database migrations..."
    node /app/server/src/db/migrate.js
fi

# Check for environment file
if [ ! -f /app/server/.env ] && [ -z "$JWT_SECRET" ]; then
    echo "WARNING: No JWT_SECRET configured. Using auto-generated ephemeral secret."
    echo "This will invalidate all sessions on restart."
    export JWT_SECRET="ephemeral-$(date +%s)-$$"
fi

# Start server
echo "Starting C2 server on port ${PORT}..."
exec "$@"
