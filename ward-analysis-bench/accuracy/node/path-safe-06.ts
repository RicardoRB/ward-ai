import * as fs from 'fs';
export function h(req: any) {
  let n = req.params.name;
  n = 'index.html';
  fs.readFile(n, () => {});
}
