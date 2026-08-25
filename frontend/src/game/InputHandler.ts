// TODO: keyboard + mouse capture → produces UserCmd each frame
export interface UserCmd {
  seq: number;
  timestamp: number;
  dx: number;
  dy: number;
  aimAngle: number;
  fire: boolean;
}

export class InputHandler {}
