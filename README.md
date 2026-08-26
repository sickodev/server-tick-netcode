# server-tick-netcode

Networked 2D top-down shooter implementing Yahn Bernier's three latency compensation techniques: Client-Side Prediction, Entity Interpolation, and Lag Compensation.

---

## 🏗️ Architecture & Stack

The repository is divided into three primary services:

| Component | Directory | Stack | Purpose | Default Port |
|---|---|---|---|---|
| **Game Service** | [`service/`](file:///g:/server-tick-netcode/service) | Go | Authoritative 64Hz tick loop, physics, lag compensation, snapshot broadcasting | `9090` (gRPC) |
| **Gateway** | [`gateway/`](file:///g:/server-tick-netcode/gateway) | Java 21 + Spring Boot 3 | Stateless WebSocket-to-gRPC bridge, rate-limiting, session authentication | `8080` (HTTP/WS) |
| **Frontend** | [`frontend/`](file:///g:/server-tick-netcode/frontend) | React + Vite + TS | Client-side prediction, input capture, 1-2 ticks entity interpolation, canvas renderer | `5173` (Dev Server) <br/> `3000` (Docker Nginx) |

---

## 📋 Prerequisites

Before running the application locally, ensure you have the following installed on your machine:

1. **Go** (version 1.20 or newer)
2. **Java Development Kit (JDK) 21**
3. **Apache Maven** (version 3.8+)
4. **Node.js** (version 18+ or 20+ LTS) and **npm**
5. **Docker & Docker Compose** (Optional, for running with containers)

---

## 🐳 Option A: Run with Docker Compose (Recommended & Quickest)

The quickest way to spin up the entire system is to use the root-level [`docker-compose.yml`](file:///g:/server-tick-netcode/docker-compose.yml) file.

### 1. Build and Start the Containers

Run the following command from the root directory:

```bash
docker-compose up --build
```

This will automatically build and start the Go service, wait for it to be healthy, start the Java gateway, wait for it to be healthy, and finally build and start the React frontend.

### 2. Access the Application

- **Frontend client**: Open your browser and navigate to [http://localhost:3000](http://localhost:3000)
- **Gateway health check**: [http://localhost:8080/health](http://localhost:8080/health) (Should return `{"status":"UP"}`)
- **Gateway WebSockets**: Connect to `ws://localhost:8080/ws`

### 3. Stop the Containers

To spin down the services:

```bash
docker-compose down
```

---

## 💻 Option B: Run Services Individually (Best for Local Development)

For rapid iteration, code hot-reloading, or using IDE debuggers, it is recommended to run each service individually in separate terminals.

### 1. Start the Game Service (Go)
The Go service runs the authoritative physics simulation at a fixed 64Hz.

```bash
cd service
go run main.go
```
* **Configuration**: By default, it listens on port `9090` (gRPC). You can customize this by setting the `GRPC_PORT` environment variable.
* **Output**: You should see `game service starting…` followed by tick updates once per second (e.g., `tick: 64`, `tick: 128`).

### 2. Start the Gateway Service (Java Spring Boot)
The Gateway bridges client WebSocket sessions to the Go gRPC backend.

```bash
cd gateway
mvn spring-boot:run
```
* **Configuration**: By default, it runs on port `8080` and looks for the Go service at `localhost:9090`. You can override this configuration via environment variables:
  * `PORT` (default `8080`)
  * `GO_SERVICE_HOST` (default `localhost`)
  * `GO_SERVICE_PORT` (default `9090`)
* **Verification**: You can verify that the gateway is up and successfully connected to the Go service by querying the health endpoint in another shell:
  ```bash
  curl http://localhost:8080/health
  # Expected output: {"status":"UP"}
  ```

### 3. Start the Frontend (React + Vite)
The frontend captures inputs, predicts local movements, and interpolates remote entity states.

1. **Verify Environment Configuration**:
   Ensure [`frontend/.env`](file:///g:/server-tick-netcode/frontend/.env) or [`frontend/.env.local`](file:///g:/server-tick-netcode/frontend/.env.local) exists. You can copy the example file:
   ```bash
   cd frontend
   cp .env.example .env
   ```
   *Make sure `VITE_GATEWAY_URL` is set to `ws://localhost:8080/ws`.*

2. **Install dependencies and start**:
   ```bash
   npm install
   npm run dev
   ```
* **Output**: The terminal will present the local URL (usually [http://localhost:5173](http://localhost:5173)).
* **Testing**: Open [http://localhost:5173](http://localhost:5173) in your browser. Open a second tab or window to test multiplayer synchronization!

---

## 🛠️ Diagnostics & Troubleshooting

* **WebSocket Connection Fails**: 
  - Ensure the Gateway is running on port `8080` and that the URL defined in your frontend `.env` matches the WebSocket endpoint `ws://localhost:8080/ws`.
  - Check the browser's developer console (F12) for connection errors.
* **Gateway Fails to Connect to Go Service**:
  - Make sure the Go service is started *first* and listening on port `9090` before starting the Gateway.
  - Check the Gateway logs for gRPC connection errors.
* **Port Conflicts**:
  - If ports `8080` or `9090` are already in use on your machine, free up the ports or customize them using the `PORT` (for Gateway) and `GRPC_PORT` (for Go service) environment variables.
