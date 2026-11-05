import { exec } from 'child_process';
import { Request } from 'express';
function runIt(value: string) {
  exec(value);
}
export function h(req: Request) {
  runIt(req.query.cmd);
}
