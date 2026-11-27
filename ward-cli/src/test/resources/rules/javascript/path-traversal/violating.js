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

// A lookup vouches for its key only when every link holds: a collection of names of the program, a
// lookup that gives a value only for a member, and a caller that exits on null.
let requestNames = null;

async function loadRequestNames() {
  if (requestNames === null) {
    requestNames = new Map();
  }
  return requestNames;
}

export function rememberName(req) {
  requestNames.set(req.query.id, 'x');
}

const lookupRequestName = async (key) => {
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

let fixedNames = null;

async function loadFixedNames() {
  if (fixedNames === null) {
    fixedNames = new Map();
    fixedNames.set('a', 'x');
  }
  return fixedNames;
}

const lookupFixedName = async (key) => {
  const known = await loadFixedNames();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return null;
};

const lookupFixedNameOrKey = async (key) => {
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

let aliasNames = null;

async function loadAliasNames() {
  if (aliasNames === null) {
    aliasNames = new Map();
  }
  return aliasNames;
}

export async function writeThroughAlias(req) {
  const names = await loadAliasNames();
  names.set(req.query.id, 'x');
}

const lookupAliasName = async (key) => {
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

const cacheWritten = {};

export function listWritten(key) {
  if (cacheWritten[key]) {
    return cacheWritten[key];
  }
  const filesWritten = fs.readdirSync('data/fixes');
  const fixes = [];
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

const cacheEnds = {};

export function listEnds(key) {
  if (cacheEnds[key]) {
    return cacheEnds[key];
  }
  const filesEnds = fs.readdirSync('data/fixes');
  const fixes = [];
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

const cacheOutside = {};

export function listOutside(key) {
  if (cacheOutside[key]) {
    return cacheOutside[key];
  }
  const filesOutside = fs.readdirSync('data/fixes');
  const fixes = [];
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

const cacheHanded = {};

export function listHanded(key) {
  if (cacheHanded[key]) {
    return cacheHanded[key];
  }
  const filesHanded = fs.readdirSync('data/fixes');
  const fixes = [];
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

const cacheStays = {};

export function listStays(key) {
  if (cacheStays[key]) {
    return cacheStays[key];
  }
  const filesStays = fs.readdirSync('data/fixes');
  const fixes = [];
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
