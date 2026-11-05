import fs from 'fs';
export function h(req: any) {
  const p = req.params.p;
  fs.createReadStream(p);
}
