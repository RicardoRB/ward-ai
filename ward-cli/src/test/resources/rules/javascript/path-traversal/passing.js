import * as fs from 'fs';
import * as path from 'path';
import { readFileSync } from 'fs';
export function handle(req) {
  const name = req.query.name;
  return readFileSync('/data/report.txt');
}

export function sanitized(req, res) {
  res.sendFile(path.resolve('ftp/', path.basename(req.params.file)));
}

export function fixed(req, res) {
  res.download('report.pdf');
}

// A keyed read picks a member of the object or map: the key adds nothing to the value.
function loadIndex() {
  return { a: 'a.txt' };
}

export function keyedObjectRead(req) {
  const index = loadIndex();
  return fs.readFileSync('public/' + index[req.query.key]);
}

export function keyedMapRead(req) {
  const names = new Map();
  names.set('a', 'a.txt');
  return fs.readFileSync('public/' + names.get(req.query.key));
}
