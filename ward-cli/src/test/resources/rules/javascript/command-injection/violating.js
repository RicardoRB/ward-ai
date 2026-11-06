import { exec } from 'child_process';
export function handle(req) {
  const cmd = req.query.cmd;
  const full = 'ls ' + cmd;
  exec(full);
}
