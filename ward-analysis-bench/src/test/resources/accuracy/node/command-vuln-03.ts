import * as cp from 'node:child_process';
export function run(req: any) {
  cp.execFile(req.query.bin, ['--version']);
}
