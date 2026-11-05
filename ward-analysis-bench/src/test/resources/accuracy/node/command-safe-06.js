import { exec } from 'child_process';
export function h(req) {
  let c = req.query.c;
  c = 'ls';
  exec(c);
}
