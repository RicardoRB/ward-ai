import * as fs from 'fs';
export function h(req) {
  const id = Number(req.params.id);
  fs.readFile('/data/' + id, () => {});
}
