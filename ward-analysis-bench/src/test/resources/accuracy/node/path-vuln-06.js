import fs from 'fs';
export function h(req) {
  fs.promises.readFile(req.query.p);
}
