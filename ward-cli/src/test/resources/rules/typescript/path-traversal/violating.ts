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

// A request member that the parameter list destructures is untrusted too.
export function destructured({ params }: Request, res) {
  res.sendFile(params.file);
}

// startsWith on a path that was never resolved is bypassed with '..'.
export function unresolved(req) {
  const target = req.query.name;
  if (!target.startsWith('public')) {
    return null;
  }
  return fs.readFileSync(target);
}

export function wrongBranch(req) {
  const target = path.resolve('public', req.query.name);
  if (target.startsWith(path.resolve('public'))) {
    console.log('inside');
  }
  return fs.readFileSync(target);
}

export function noExit(req) {
  const target = path.resolve('public', req.query.name);
  if (!target.startsWith(path.resolve('public'))) {
    console.log('outside');
  }
  return fs.readFileSync(target);
}

export function exitOnlyWithAnother(req) {
  const target = path.resolve('public', req.query.name);
  if (!target.startsWith(path.resolve('public')) && req.query.strict) {
    return null;
  }
  return fs.readFileSync(target);
}

export function tableOfRuntimeValues(req, table) {
  const file = table[req.query.kind];
  return fs.readFileSync(file);
}
