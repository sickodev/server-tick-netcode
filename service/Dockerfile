FROM golang:1.22-alpine AS build
WORKDIR /app

# Download deps first (layer cache)
COPY go.mod go.sum ./
RUN go mod download

# Build
COPY . .
RUN go build -o gameserver main.go

# Minimal runtime image
FROM alpine:3.19
WORKDIR /app
COPY --from=build /app/gameserver .
EXPOSE 9090
ENTRYPOINT ["./gameserver"]
