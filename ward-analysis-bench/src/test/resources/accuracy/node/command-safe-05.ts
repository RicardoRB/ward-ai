import * as cp from 'child_process';
export function h(req: any) {
  cp.execFile('ls', [req.query.x]);
}
