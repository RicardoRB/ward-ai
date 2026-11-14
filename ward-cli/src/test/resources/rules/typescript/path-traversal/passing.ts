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

export function destructuredUnused({ params }: Request, res) {
  res.sendFile('index.html');
}
