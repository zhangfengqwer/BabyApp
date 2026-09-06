FROM node:22-bookworm-slim AS base
RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl \
    && rm -rf /var/lib/apt/lists/*

FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/backend/package.json ./apps/backend/package.json
RUN npm ci

FROM deps AS build
COPY apps/backend/prisma ./apps/backend/prisma
COPY apps/backend/nest-cli.json apps/backend/tsconfig*.json ./apps/backend/
COPY apps/backend/src ./apps/backend/src
RUN npm --workspace apps/backend run prisma:generate \
    && npm --workspace apps/backend run prisma:seed:build \
    && npm run backend:build

FROM base AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=deps /app/node_modules ./node_modules
COPY --from=build /app/apps/backend/node_modules ./apps/backend/node_modules
COPY --from=build /app/apps/backend/dist ./apps/backend/dist
COPY --from=build /app/apps/backend/dist-seed ./apps/backend/dist-seed
COPY --from=build /app/apps/backend/prisma ./apps/backend/prisma
COPY package.json package-lock.json ./
COPY apps/backend/package.json ./apps/backend/package.json
EXPOSE 8080
CMD ["sh", "-c", "./apps/backend/node_modules/.bin/prisma migrate deploy --schema apps/backend/prisma/schema.prisma && node apps/backend/dist/main.js"]
