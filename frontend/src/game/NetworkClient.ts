import {
  ClientMessage,
  JoinMessage,
  JoinResponseMessage,
  Snapshot,
  UserCmd,
  UserCmdMessage,
} from './types';

/** Default initial exponential backoff reconnect delay in milliseconds */
const INITIAL_RECONNECT_DELAY_MS = 1000;

/** Maximum reconnect backoff delay in milliseconds */
const MAX_RECONNECT_DELAY_MS = 5000;

/**
 * WebSocket network client managing bidirectional communication with the game gateway.
 *
 * Handles socket lifecycle (connection establishment, graceful shutdown, exponential backoff
 * reconnection), session player identification, initial join handshakes, high-frequency user
 * command streaming, and authoritative server snapshot dispatch.
 */
export class NetworkClient {
  /** Target gateway WebSocket URL (derived from Vite environment or default fallback) */
  private gatewayUrl: string;

  /** Unique player identifier consistent for the lifetime of this browser session */
  private playerId: string;

  /** Active WebSocket connection instance or null if disconnected */
  private socket: WebSocket | null = null;

  /** Current backoff delay in milliseconds for reconnection attempts */
  private reconnectDelayMs: number = INITIAL_RECONNECT_DELAY_MS;

  /** Timer handle for pending reconnection attempt */
  private reconnectTimer: number | null = null;

  /** Flag indicating whether the connection was closed intentionally by the client */
  private isExplicitlyClosed: boolean = false;

  /** Registered subscriber callbacks for incoming server snapshots */
  private snapshotListeners: Array<(snapshot: Snapshot) => void> = [];

  /** Registered subscriber callbacks for join acknowledgment responses */
  private joinResponseListeners: Array<(response: JoinResponseMessage) => void> = [];

  /** Registered subscriber callbacks for connection open events */
  private connectListeners: Array<() => void> = [];

  /** Registered subscriber callbacks for connection close events */
  private disconnectListeners: Array<() => void> = [];

  /**
   * Initializes the NetworkClient with a gateway URL and a persistent player UUID.
   *
   * @param gatewayUrl - Optional WebSocket URL override. If omitted, reads from `VITE_GATEWAY_URL`.
   */
  constructor(gatewayUrl?: string) {
    this.gatewayUrl =
      gatewayUrl ||
      (import.meta.env.VITE_GATEWAY_URL as string | undefined) ||
      'ws://localhost:8080/ws';

    // Generate a RFC 4122 compliant UUID v4 for the player session
    this.playerId =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : this.generateFallbackUuid();
  }

  /**
   * Generates a pseudo-random UUID v4 string if crypto.randomUUID is unavailable.
   *
   * @returns Formatted UUID string.
   */
  private generateFallbackUuid(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
      const random = (Math.random() * 16) | 0;
      const value = char === 'x' ? random : (random & 0x3) | 0x8;
      return value.toString(16);
    });
  }

  /**
   * Retrieves the persistent unique player identifier for this client session.
   *
   * @returns The session UUID string.
   */
  public getPlayerId(): string {
    return this.playerId;
  }

  /**
   * Checks whether the WebSocket connection is currently open and ready for transmission.
   *
   * @returns True if connected, false otherwise.
   */
  public isConnected(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  /**
   * Establishes a WebSocket connection to the game gateway and registers event listeners.
   */
  public connect(): void {
    // Prevent duplicate connection attempts if already active or connecting
    if (
      this.socket &&
      (this.socket.readyState === WebSocket.CONNECTING ||
        this.socket.readyState === WebSocket.OPEN)
    ) {
      return;
    }

    this.isExplicitlyClosed = false;
    this.clearReconnectTimer();

    try {
      this.socket = new WebSocket(this.gatewayUrl);

      this.socket.onopen = this.handleOpen.bind(this);
      this.socket.onmessage = this.handleMessage.bind(this);
      this.socket.onclose = this.handleClose.bind(this);
      this.socket.onerror = this.handleError.bind(this);
    } catch (error) {
      console.log('[ws] error: Failed to instantiate WebSocket connection', error);
      this.scheduleReconnect();
    }
  }

  /**
   * Gracefully terminates the active WebSocket connection and halts automatic reconnection.
   */
  public disconnect(): void {
    this.isExplicitlyClosed = true;
    this.clearReconnectTimer();

    if (this.socket) {
      // Unbind handlers to avoid triggering reconnect logic during intentional teardown
      this.socket.onopen = null;
      this.socket.onmessage = null;
      this.socket.onclose = null;
      this.socket.onerror = null;

      if (
        this.socket.readyState === WebSocket.OPEN ||
        this.socket.readyState === WebSocket.CONNECTING
      ) {
        this.socket.close();
      }
      this.socket = null;
    }

    console.log('[ws] disconnected');
    this.disconnectListeners.forEach((listener) => listener());
  }

  /**
   * Internal handler triggered upon successful WebSocket connection handshake.
   * Resets reconnection backoff delay and transmits the initial join message.
   */
  private handleOpen(): void {
    console.log('[ws] connected');
    this.reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

    // Transmit initial join payload to gateway
    const joinMessage: JoinMessage = {
      type: 'join',
      playerId: this.playerId,
      name: 'Player',
    };

    this.send(joinMessage);
    console.log('[ws] sent join message:', joinMessage);

    this.connectListeners.forEach((listener) => listener());
  }

  /**
   * Internal handler triggered when a text message frame is received from the gateway.
   *
   * @param event - MessageEvent containing raw text payload.
   */
  private handleMessage(event: MessageEvent): void {
    try {
      const data = JSON.parse(event.data);
      if (!data || typeof data !== 'object') {
        return;
      }

      const type = data.type;

      if (type === 'snapshot' || ('entities' in data && 'serverTick' in data) || ('entities' in data && 'server_tick' in data)) {
        const snapshot = data as Snapshot;
        const tick = snapshot.serverTick ?? snapshot.server_tick ?? 0;
        const ack = snapshot.ackSeq ?? snapshot.ack_seq ?? 0;
        console.log(`[ws] snapshot tick=${tick}, ackSeq=${ack}`);

        this.snapshotListeners.forEach((listener) => listener(snapshot));
      } else if (type === 'joinResponse') {
        const joinResponse = data as JoinResponseMessage;
        console.log('[ws] received join response:', joinResponse);

        this.joinResponseListeners.forEach((listener) => listener(joinResponse));
      } else {
        console.log('[ws] received unhandled message:', data);
      }
    } catch (error) {
      console.log('[ws] error: Failed to parse incoming JSON message frame', error);
    }
  }

  /**
   * Internal handler triggered when the WebSocket connection is closed.
   *
   * @param event - CloseEvent containing close code and reason.
   */
  private handleClose(event: CloseEvent): void {
    console.log(`[ws] disconnected (code: ${event.code})`);
    this.socket = null;
    this.disconnectListeners.forEach((listener) => listener());

    if (!this.isExplicitlyClosed) {
      this.scheduleReconnect();
    }
  }

  /**
   * Internal handler triggered on WebSocket communication errors.
   *
   * @param event - Error event details.
   */
  private handleError(event: Event): void {
    console.log('[ws] error:', event);
  }

  /**
   * Schedules an automatic reconnection attempt using exponential backoff.
   */
  private scheduleReconnect(): void {
    if (this.isExplicitlyClosed || this.reconnectTimer !== null) {
      return;
    }

    const delay = this.reconnectDelayMs;
    console.log(`[ws] reconnecting in ${delay}ms...`);

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);

    // Double backoff delay for subsequent failure, capped at MAX_RECONNECT_DELAY_MS
    this.reconnectDelayMs = Math.min(this.reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
  }

  /**
   * Cancels any pending reconnection timer.
   */
  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * Packages and transmits a frame UserCmd to the server.
   *
   * @param cmd - The input command captured for the current frame.
   * @returns True if successfully dispatched, false if socket was not open.
   */
  public sendUserCmd(cmd: UserCmd): boolean {
    if (!this.isConnected()) {
      return false;
    }

    const message: UserCmdMessage = {
      type: 'user_cmd',
      seq: cmd.seq,
      timestamp: cmd.timestamp,
      dx: cmd.dx,
      dy: cmd.dy,
      aimAngle: cmd.aimAngle,
      aim_angle: cmd.aimAngle,
      fire: cmd.fire,
    };

    return this.send(message);
  }

  /**
   * Serializes and dispatches a JSON payload over the WebSocket connection.
   *
   * @param message - The client message payload to transmit.
   * @returns True if sent successfully, false otherwise.
   */
  public send(message: ClientMessage | object): boolean {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return false;
    }

    try {
      this.socket.send(JSON.stringify(message));
      return true;
    } catch (error) {
      console.log('[ws] error: Failed to send WebSocket message', error);
      return false;
    }
  }

  /**
   * Registers a subscriber callback to receive incoming authoritative snapshots.
   *
   * @param callback - Function invoked whenever a snapshot arrives.
   * @returns Unsubscribe function to remove the listener.
   */
  public onSnapshot(callback: (snapshot: Snapshot) => void): () => void {
    this.snapshotListeners.push(callback);
    return () => {
      this.snapshotListeners = this.snapshotListeners.filter((listener) => listener !== callback);
    };
  }

  /**
   * Registers a subscriber callback for player join responses.
   *
   * @param callback - Function invoked on join acknowledgment.
   * @returns Unsubscribe function to remove the listener.
   */
  public onJoinResponse(callback: (response: JoinResponseMessage) => void): () => void {
    this.joinResponseListeners.push(callback);
    return () => {
      this.joinResponseListeners = this.joinResponseListeners.filter(
        (listener) => listener !== callback
      );
    };
  }

  /**
   * Registers a subscriber callback for connection open events.
   *
   * @param callback - Function invoked on WebSocket open.
   * @returns Unsubscribe function to remove the listener.
   */
  public onConnect(callback: () => void): () => void {
    this.connectListeners.push(callback);
    return () => {
      this.connectListeners = this.connectListeners.filter((listener) => listener !== callback);
    };
  }

  /**
   * Registers a subscriber callback for connection disconnect events.
   *
   * @param callback - Function invoked on WebSocket close.
   * @returns Unsubscribe function to remove the listener.
   */
  public onDisconnect(callback: () => void): () => void {
    this.disconnectListeners.push(callback);
    return () => {
      this.disconnectListeners = this.disconnectListeners.filter(
        (listener) => listener !== callback
      );
    };
  }
}
