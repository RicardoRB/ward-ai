import { exec } from 'child_process';
import { Request } from 'express';
export function h(request: Request) {
  const cmd = request.body.cmd;
  exec(cmd);
}
