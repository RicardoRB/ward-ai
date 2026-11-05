import * as fs from 'fs';
export function h(req) {
  fs.readFile('./static/index.html', () => {});
}
