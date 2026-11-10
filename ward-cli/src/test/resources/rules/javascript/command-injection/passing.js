import { exec } from 'child_process';
export function handle(req) {
  const cmd = req.query.cmd;
  exec('ls');
}
