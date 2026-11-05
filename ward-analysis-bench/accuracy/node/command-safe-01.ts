import { exec } from 'child_process';
export function h(req: any) {
  exec('git status');
  return req.query.x;
}
