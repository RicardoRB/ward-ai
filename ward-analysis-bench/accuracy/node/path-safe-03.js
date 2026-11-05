import path from 'path';
import * as fs from 'fs';
export function h(req) {
  const p = path.join('/srv', req.params.x);
  fs.readFile('/srv/static.html', () => {});
}
