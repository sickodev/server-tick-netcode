# 🚀 Deployment Guide — server-tick-netcode

This guide explains how to deploy the entire `server-tick-netcode` stack (React Frontend, Java Gateway, and Go Game Service) to production.

---

## 🏗️ Architecture Overview

The application utilizes a distributed layout to keep deployments efficient and cost-effective:

```mermaid
graph TD
    Client[Web Browser] -->|HTTPS/HTML/JS| Vercel[Vercel <br/><i>Frontend (Public)</i>]
    Client -->|WSS WebSocket| RenderGat[Render <br/><i>Java Gateway (Public)</i>]
    RenderGat -->|gRPC via Private Network| RenderSer[Render <br/><i>Go Game Service (Private)</i>]
```

---

## 1. Backend Deployment on Render (Recommended)

We use **Render Blueprints** to deploy both backend components (Go service and Java gateway) simultaneously. The gateway connects to the Go server using Render's private service-to-service discovery.

### Step-by-Step Deployment:
1. Push your repository containing the root-level [`render.yaml`](file:///g:/server-tick-netcode/render.yaml) to GitHub.
2. Sign in to your [Render Dashboard](https://dashboard.render.com).
3. Click **New +** and select **Blueprint**.
4. Connect your GitHub repository.
5. Render will automatically detect [`render.yaml`](file:///g:/server-tick-netcode/render.yaml) and prompt you to create the services:
   * **`tick-gameserver`** (Go service, runs privately on port `9090`).
   * **`tick-gateway`** (Java Gateway, runs publicly and exposes WebSockets).
6. Click **Apply**.
7. Once successfully deployed, note down the public URL of the **`tick-gateway`** service (e.g., `https://tick-gateway.onrender.com`).

---

## 2. Frontend Deployment on Vercel

Vercel is the recommended host for the React + Vite static frontend.

### Step-by-Step Deployment:
1. Sign in to your [Vercel Dashboard](https://vercel.com).
2. Click **Add New > Project** and import your repository.
3. Configure the Project Settings:
   - **Root Directory**: `frontend/`
   - **Framework Preset**: `Vite`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
4. **Configure Environment Variables**:
   Under the Environment Variables section, add:
   * **Key**: `VITE_GATEWAY_URL`
   * **Value**: `wss://tick-gateway.onrender.com/ws` *(replace with your deployed Render gateway domain and append `/ws`)*
5. Click **Deploy**.

---

## 🧪 Post-Deployment Verification

1. **Gateway Health**: Query the health check endpoint of your deployed gateway to verify it is online and running:
   ```bash
   curl https://your-gateway-url.onrender.com/health
   ```
   *Expected Response:* `{"status":"UP"}`

2. **Frontend Connection**: Open your deployed Vercel URL, launch the browser's developer console (F12), and verify that the WebSocket handshake completes successfully without any connection errors.
