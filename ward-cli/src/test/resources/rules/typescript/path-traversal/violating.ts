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

// A list that the program does not write out in full is not an allow-list.
export function listFromParameter(req, allowed) {
  const kind = req.query.kind;
  const file = allowed.includes(kind) ? kind : 'logo';
  return fs.readFileSync('images/' + file);
}

export function listWithRuntimeMember(req) {
  const kind = req.query.kind;
  if (['avatar', req.query.other].includes(kind)) {
    return fs.readFileSync('images/' + kind);
  }
  return undefined;
}

// The ternary must give the tested value or a constant.
export function ternaryOtherBranch(req) {
  const kind = req.query.kind;
  const file = ['avatar', 'logo'].includes(kind) ? kind : req.query.fallback;
  return fs.readFileSync('images/' + file);
}

export function ternaryOtherValue(req) {
  const kind = req.query.kind;
  const file = ['avatar', 'logo'].includes(kind) ? req.query.other : 'logo';
  return fs.readFileSync('images/' + file);
}

// A guard that does not exit leaves the value as it was.
const GUARD_LIST = ['avatar', 'logo'];

export function guardWithoutExit(req) {
  const kind = req.query.kind;
  if (!GUARD_LIST.includes(kind)) {
    console.log('unknown kind');
  }
  return fs.readFileSync('images/' + kind);
}

function warnKind(value) {
  if (!GUARD_LIST.includes(value)) {
    console.log('unknown kind');
  }
}

export function validatorWithoutExit(req) {
  const kind = req.query.kind;
  warnKind(kind);
  return fs.readFileSync('images/' + kind);
}

function skipKind(value) {
  if (!GUARD_LIST.includes(value)) {
    return;
  }
}

export function validatorReturnsEarly(req) {
  const kind = req.query.kind;
  skipKind(kind);
  return fs.readFileSync('images/' + kind);
}

function reassigned(value, extra) {
  if (!GUARD_LIST.includes(value)) {
    throw new Error('unknown kind');
  }
  value = value + extra;
}

export function validatedThenMutated(req) {
  const kind = req.query.kind;
  reassigned(kind, req.query.extra);
  return fs.readFileSync('images/' + kind);
}

export function appendedAfterGuard(req) {
  let kind = req.query.kind;
  if (!GUARD_LIST.includes(kind)) {
    throw new Error('unknown kind');
  }
  kind = kind + req.query.suffix;
  return fs.readFileSync('images/' + kind);
}

// A value that a logical or conditional operator picks is untrusted when one operand is.
export function defaulted(req) {
  const file = req.query.file || 'index.html';
  return fs.readFileSync('public/' + file);
}

export function conditional(req) {
  const file = req.query.file ? req.query.file : 'index.html';
  return fs.readFileSync('public/' + file);
}

export function assignedConditional(req) {
  let file = 'index.html';
  file = req.query.other ? req.query.other : file;
  return fs.readFileSync('public/' + file);
}
