FROM node:20-alpine AS build
WORKDIR /app

# Download deps first (layer cache)
COPY package*.json ./
RUN npm ci

# Inject gateway URL at build time
ARG VITE_GATEWAY_URL=ws://localhost:8080/ws
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL

# Build static assets
COPY . .
RUN npm run build

# Serve with nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
