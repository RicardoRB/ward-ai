import { exec } from 'child_process';
export function h(req: any) {
  const n = Number(req.query.n);
  exec('ls -l ' + n);
}
