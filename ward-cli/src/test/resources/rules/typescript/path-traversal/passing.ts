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

// A path that was resolved and then checked against a base is inside it.
export function contained(req, res) {
  const target = path.resolve('public', req.path);
  if (!target.startsWith(path.resolve('public')) || target.endsWith('.env')) {
    return res.status(403).end();
  }
  return fs.readFileSync(target);
}

export function containedThrow(req) {
  const target = path.join('public', req.query.name);
  if (!target.startsWith('public')) {
    throw new Error('outside the folder');
  }
  return fs.readFileSync(target);
}

export function containedInside(req) {
  const target = path.resolve('public', req.query.name);
  if (target.startsWith(path.resolve('public'))) {
    return fs.readFileSync(target);
  }
  return undefined;
}

// Whatever key a lookup uses, a table of constants answers one of its own members.
const KINDS = { avatar: 'images/avatar.png', logo: 'images/logo.png' };
const FILES = new Map([['a', 'a.txt'], ['b', 'b.txt']]);

export function tableLookup(req) {
  const file = KINDS[req.query.kind];
  return fs.readFileSync(file);
}

export function mapLookup(req) {
  const file = FILES.get(req.params.id);
  return fs.readFileSync(file);
}
