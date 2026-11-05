import * as fs from 'fs';
function open(p: string) {
  return fs.readFileSync(p);
}
export function h(req: any) {
  open(req.query.p);
}
