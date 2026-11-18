# Build context is the repo root (see docker-compose.yml): Vite copies ../sample-data into dist/.
# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
COPY sample-data /src/sample-data
# Default "/api" = same origin, proxied by nginx below. For a separate backend host pass
#   --build-arg VITE_API_BASE_URL=https://api.example.com/api
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# --- runtime stage: nginx serves dist/ ---
FROM nginx:1.27-alpine
# Upstream for /api (the compose service name by default). nginx's image fills it into the template at start.
ENV BACKEND_URL=http://backend:8080
COPY frontend/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/frontend/dist /usr/share/nginx/html
EXPOSE 80
