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

// A lookup vouches for its key only when every link holds: a collection of names of the program, a
// lookup that gives a value only for a member, and a caller that exits on null.
let requestNames: Map<string, string> | null = null;

async function loadRequestNames() {
  if (requestNames === null) {
    requestNames = new Map<string, string>();
  }
  return requestNames;
}

export function rememberName(req) {
  requestNames.set(req.query.id, 'x');
}

const lookupRequestName = async (key: string) => {
  const known = await loadRequestNames();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return null;
};

export async function readRequestName(req, res) {
  const key = req.query.key;
  const found = await lookupRequestName(key);
  if (found == null) {
    return res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

let fixedNames: Map<string, string> | null = null;

async function loadFixedNames() {
  if (fixedNames === null) {
    fixedNames = new Map<string, string>();
    fixedNames.set('a', 'x');
  }
  return fixedNames;
}

const lookupFixedName = async (key: string) => {
  const known = await loadFixedNames();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return null;
};

const lookupFixedNameOrKey = async (key: string) => {
  const known = await loadFixedNames();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return key;
};

export async function readWithoutExit(req, res) {
  const key = req.query.key;
  const found = await lookupFixedName(key);
  if (found == null) {
    res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

export async function readWithoutAwait(req, res) {
  const key = req.query.key;
  const found = lookupFixedName(key);
  if (found == null) {
    return res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

export async function readFromOtherReturn(req, res) {
  const key = req.query.key;
  const found = await lookupFixedNameOrKey(key);
  if (found == null) {
    return res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

let aliasNames: Map<string, string> | null = null;

async function loadAliasNames() {
  if (aliasNames === null) {
    aliasNames = new Map<string, string>();
  }
  return aliasNames;
}

export async function writeThroughAlias(req) {
  const names = await loadAliasNames();
  names.set(req.query.id, 'x');
}

const lookupAliasName = async (key: string) => {
  const known = await loadAliasNames();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return null;
};

export async function readAfterAliasWrite(req, res) {
  const key = req.query.key;
  const found = await lookupAliasName(key);
  if (found == null) {
    return res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

// A listing of a directory proves a key only when each link holds: the cache is the function's
// alone, only entries that start with the key reach the list, and the caller leaves on an empty list.

const cacheWritten: Record<string, { fixes: string[] }> = {};

export function listWritten(key: string) {
  if (cacheWritten[key]) {
    return cacheWritten[key];
  }
  const filesWritten = fs.readdirSync('data/fixes');
  const fixes: string[] = [];
  for (const file of filesWritten) {
    if (file.startsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  cacheWritten[key] = { fixes };
  return cacheWritten[key];
}

export function readWritten(req, res) {
  const key = req.params.key;
  const data = listWritten(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    return;
  }
  return fs.readFileSync('public/' + key + '.yml');
}

export function overwrite(req) {
  cacheWritten[req.query.slot] = { fixes: ['x'] };
}

const cacheEnds: Record<string, { fixes: string[] }> = {};

export function listEnds(key: string) {
  if (cacheEnds[key]) {
    return cacheEnds[key];
  }
  const filesEnds = fs.readdirSync('data/fixes');
  const fixes: string[] = [];
  for (const file of filesEnds) {
    if (file.endsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  cacheEnds[key] = { fixes };
  return cacheEnds[key];
}

export function readEnds(req, res) {
  const key = req.params.key;
  const data = listEnds(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    return;
  }
  return fs.readFileSync('public/' + key + '.yml');
}

const cacheOutside: Record<string, { fixes: string[] }> = {};

export function listOutside(key: string) {
  if (cacheOutside[key]) {
    return cacheOutside[key];
  }
  const filesOutside = fs.readdirSync('data/fixes');
  const fixes: string[] = [];
  for (const file of filesOutside) {
    if (file.startsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  fixes.push(key);
  cacheOutside[key] = { fixes };
  return cacheOutside[key];
}

export function readOutside(req, res) {
  const key = req.params.key;
  const data = listOutside(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    return;
  }
  return fs.readFileSync('public/' + key + '.yml');
}

const cacheHanded: Record<string, { fixes: string[] }> = {};

export function listHanded(key: string) {
  if (cacheHanded[key]) {
    return cacheHanded[key];
  }
  const filesHanded = fs.readdirSync('data/fixes');
  const fixes: string[] = [];
  for (const file of filesHanded) {
    if (file.startsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  register(fixes);
  cacheHanded[key] = { fixes };
  return cacheHanded[key];
}

export function readHanded(req, res) {
  const key = req.params.key;
  const data = listHanded(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    return;
  }
  return fs.readFileSync('public/' + key + '.yml');
}

const cacheStays: Record<string, { fixes: string[] }> = {};

export function listStays(key: string) {
  if (cacheStays[key]) {
    return cacheStays[key];
  }
  const filesStays = fs.readdirSync('data/fixes');
  const fixes: string[] = [];
  for (const file of filesStays) {
    if (file.startsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  cacheStays[key] = { fixes };
  return cacheStays[key];
}

export function readStays(req, res) {
  const key = req.params.key;
  const data = listStays(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    log();
  }
  return fs.readFileSync('public/' + key + '.yml');
}
