import * as fs from 'fs';
export function h(req) {
  fs.readFile(req.query.f, () => {});
}
