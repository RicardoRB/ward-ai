import * as fs from 'fs';
import * as path from 'path';
import { readFileSync } from 'fs';
export function handle(req) {
  const name = req.query.name;
  return readFileSync('/data/' + name);
}

export function sendFromFolder(req, res) {
  const file = req.params.file;
  res.sendFile(path.resolve('ftp/', file));
}

export function download(req, res) {
  res.download(req.query.name);
}

export function joined(req) {
  const name = req.body.name;
  fs.writeFileSync(path.join('uploads', name), 'x');
}

// A keyed read of an object or map that holds untrusted values is untrusted.
export function keyedReadOfTaintedObject(req) {
  const index = req.body.index;
  return fs.readFileSync('public/' + index[req.query.key]);
}

export function keyedReadOfTaintedMap(req) {
  const names = new Map([['a', req.query.name]]);
  return fs.readFileSync('public/' + names.get('a'));
}

// A detector of another package is not known to return only its own table's extensions.
import detector from 'other-detector';
export async function unknownDetector(req) {
  const detected = await detector.fromBuffer(req.body.data);
  return fs.readFileSync('uploads/1.' + detected.ext);
}
