import { exec } from 'child_process';
export function handle(req) {
  const cmd = req.query.cmd;
  const full = 'ls ' + cmd;
  exec(full);
}

export function shell(req: any, shelljs: any) {
  const cmd = req.query.cmd;
  shelljs.exec(cmd);
}

export function run(req: any) {
  const name = req.query.name;
  return execa(name);
}
