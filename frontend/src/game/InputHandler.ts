import { ARENA_W, ARENA_H } from './constants';

/**
 * Encapsulates the normalized 2D movement vector derived from active input controls.
 */
export interface MovementVector {
  /** Normalized horizontal movement component (-1.0 to 1.0) */
  dx: number;
  /** Normalized vertical movement component (-1.0 to 1.0) */
  dy: number;
}

/**
 * Authoritative user command payload captured for a single frame or simulation tick.
 */
export interface UserCmd {
  /** Monotonically increasing sequence number for this command */
  seq: number;
  /** Client-side timestamp in milliseconds when input was captured */
  timestamp: number;
  /** Normalized horizontal movement delta (-1 to 1) */
  dx: number;
  /** Normalized vertical movement delta (-1 to 1) */
  dy: number;
  /** Aim direction angle in radians (facing direction toward mouse cursor) */
  aimAngle: number;
  /** Whether the primary fire action was triggered in this frame */
  fire: boolean;
}

/**
 * Handles keyboard and mouse event capture for the game viewport.
 *
 * Translates raw DOM input events into normalized movement directions (WASD / Arrow keys),
 * canvas-space mouse coordinates, and aim angles relative to the player's position.
 */
export class InputHandler {
  private canvas: HTMLCanvasElement;
  private heldKeys: Set<string> = new Set();
  private mouseX: number = ARENA_W / 2;
  private mouseY: number = ARENA_H / 2;
  private isMouseDown: boolean = false;

  // Bound event listener references for robust cleanup on unmount
  private boundKeyDown: (e: KeyboardEvent) => void;
  private boundKeyUp: (e: KeyboardEvent) => void;
  private boundMouseMove: (e: MouseEvent) => void;
  private boundMouseDown: (e: MouseEvent) => void;
  private boundMouseUp: (e: MouseEvent) => void;

  /**
   * Initializes input listeners on the canvas and global window object.
   *
   * @param canvas - The HTML5 canvas DOM element used for spatial coordinate translation.
   */
  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;

    this.boundKeyDown = this.handleKeyDown.bind(this);
    this.boundKeyUp = this.handleKeyUp.bind(this);
    this.boundMouseMove = this.handleMouseMove.bind(this);
    this.boundMouseDown = this.handleMouseDown.bind(this);
    this.boundMouseUp = this.handleMouseUp.bind(this);

    this.attachListeners();
  }

  /**
   * Attaches DOM event listeners to window and canvas.
   */
  private attachListeners(): void {
    window.addEventListener('keydown', this.boundKeyDown);
    window.addEventListener('keyup', this.boundKeyUp);
    window.addEventListener('mousemove', this.boundMouseMove);
    window.addEventListener('mousedown', this.boundMouseDown);
    window.addEventListener('mouseup', this.boundMouseUp);
  }

  /**
   * Detaches all registered DOM event listeners to prevent memory leaks.
   */
  public cleanup(): void {
    window.removeEventListener('keydown', this.boundKeyDown);
    window.removeEventListener('keyup', this.boundKeyUp);
    window.removeEventListener('mousemove', this.boundMouseMove);
    window.removeEventListener('mousedown', this.boundMouseDown);
    window.removeEventListener('mouseup', this.boundMouseUp);
    this.heldKeys.clear();
  }

  /**
   * Records active key presses into the held keys set.
   *
   * @param event - Keyboard DOM event.
   */
  private handleKeyDown(event: KeyboardEvent): void {
    this.heldKeys.add(event.code);
  }

  /**
   * Removes released keys from the held keys set.
   *
   * @param event - Keyboard DOM event.
   */
  private handleKeyUp(event: KeyboardEvent): void {
    this.heldKeys.delete(event.code);
  }

  /**
   * Translates client mouse coordinates into arena canvas space.
   * Accounts for canvas bounding rectangle dimensions and CSS scaling factors.
   *
   * @param event - Mouse DOM event.
   */
  private handleMouseMove(event: MouseEvent): void {
    const rect = this.canvas.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return;
    }

    const scaleX = this.canvas.width / rect.width;
    const scaleY = this.canvas.height / rect.height;

    this.mouseX = (event.clientX - rect.left) * scaleX;
    this.mouseY = (event.clientY - rect.top) * scaleY;
  }

  /**
   * Records mouse button press state.
   *
   * @param event - Mouse DOM event.
   */
  private handleMouseDown(event: MouseEvent): void {
    if (event.button === 0) {
      this.isMouseDown = true;
    }
  }

  /**
   * Records mouse button release state.
   *
   * @param event - Mouse DOM event.
   */
  private handleMouseUp(event: MouseEvent): void {
    if (event.button === 0) {
      this.isMouseDown = false;
    }
  }

  /**
   * Computes the normalized directional movement vector based on currently held movement keys.
   * Supports WASD and Arrow key mappings with diagonal normalization to maintain constant speed.
   *
   * @returns Normalized MovementVector with dx and dy components (-1 to 1).
   */
  public getMovementVector(): MovementVector {
    let rawDx = 0;
    let rawDy = 0;

    // Vertical movement (W / Up / S / Down)
    if (this.heldKeys.has('KeyW') || this.heldKeys.has('ArrowUp')) {
      rawDy -= 1;
    }
    if (this.heldKeys.has('KeyS') || this.heldKeys.has('ArrowDown')) {
      rawDy += 1;
    }

    // Horizontal movement (A / Left / D / Right)
    if (this.heldKeys.has('KeyA') || this.heldKeys.has('ArrowLeft')) {
      rawDx -= 1;
    }
    if (this.heldKeys.has('KeyD') || this.heldKeys.has('ArrowRight')) {
      rawDx += 1;
    }

    // Return zero movement if no movement keys are active
    if (rawDx === 0 && rawDy === 0) {
      return { dx: 0, dy: 0 };
    }

    // Normalize diagonal movement vector so diagonal speed equals cardinal speed
    const magnitude = Math.hypot(rawDx, rawDy);
    return {
      dx: rawDx / magnitude,
      dy: rawDy / magnitude,
    };
  }

  /**
   * Calculates the directional aim angle in radians from a given player position to the mouse cursor.
   *
   * @param playerX - Player horizontal position in arena canvas coordinates.
   * @param playerY - Player vertical position in arena canvas coordinates.
   * @returns Aim angle in radians (0 = facing right, positive clockwise in canvas coordinates).
   */
  public getAimAngle(playerX: number, playerY: number): number {
    return Math.atan2(this.mouseY - playerY, this.mouseX - playerX);
  }

  /**
   * Retrieves the current mouse position in canvas coordinates.
   *
   * @returns Object containing horizontal x and vertical y coordinates.
   */
  public getMousePosition(): { x: number; y: number } {
    return { x: this.mouseX, y: this.mouseY };
  }

  /**
   * Checks whether the player is currently pressing fire (left mouse button or Spacebar).
   *
   * @returns True if fire input is active, false otherwise.
   */
  public isFiring(): boolean {
    return this.isMouseDown || this.heldKeys.has('Space');
  }
}
