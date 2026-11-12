import { exec } from 'child_process';
export function handle(req) {
  const cmd = req.query.cmd;
  exec('ls');
}

export function shell(req, shelljs) {
  const cmd = req.query.cmd;
  shelljs.exec('ls');
  execa('ls');
}
