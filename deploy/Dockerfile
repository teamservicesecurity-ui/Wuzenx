# ============================================================
# FILE: deploy/Dockerfile
# ============================================================
FROM node:20-slim AS base
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ============================================================
# Stage 1: Server dependencies
# ============================================================
FROM base AS server-deps
COPY server/package*.json ./server/
RUN cd server && npm ci --production --ignore-scripts

# ============================================================
# Stage 2: Final image
# ============================================================
FROM base AS final
ENV NODE_ENV=production
ENV PORT=3000

# Copy server code
COPY --from=server-deps /app/server/node_modules ./server/node_modules
COPY server/ ./server/

# Copy built dashboard
COPY dashboard/dist/ ./dashboard/dist/

# Copy deploy configs
COPY deploy/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Expose C2 API + WebSocket
EXPOSE 3000
EXPOSE 443

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]
CMD ["node", "server/index.js"]
