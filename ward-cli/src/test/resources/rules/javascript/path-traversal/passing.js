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

import fileType from 'file-type';
export async function detectedExtension(req) {
  const detected = await fileType.fromBuffer(req.body.data);
  return fs.readFileSync('uploads/1.' + detected.ext);
}

// A key that a lookup accepted into a collection that only this file writes to, with names of the
// program, and that the caller checked for null, is one of those names.
let knownNames = null;

async function loadKnown() {
  if (knownNames === null) {
    knownNames = new Map();
    for (const name of ['a', 'b']) {
      knownNames.set(name, 'x');
    }
  }
  return knownNames;
}

const lookupKnown = async (key) => {
  const known = await loadKnown();
  if (known.has(key)) {
    return known.get(key) ?? null;
  }
  return null;
};

export async function readKnown(req, res) {
  const key = req.query.key;
  let found;
  try {
    found = await lookupKnown(key);
    if (found == null) {
      res.status(404).end();
      return;
    }
  } catch (error) {
    return;
  }
  return fs.readFileSync('public/' + key);
}

export async function readKnownFalsy(req, res) {
  const key = req.query.key;
  const found = await lookupKnown(key);
  if (!found) {
    return res.status(404).end();
  }
  return fs.readFileSync('public/' + key);
}

// A key that a cached listing of a directory proved to start the name of an entry holds no path
// separator: the function lists an entry only for a key that starts its name, and the caller left
// or took the other branch when the list was empty.
const listingCache = {};

export function listFixes(key) {
  if (listingCache[key]) {
    return listingCache[key];
  }
  const files = fs.readdirSync('data/fixes');
  const fixes = [];
  for (const file of files) {
    if (file.startsWith(`${key}_`)) {
      fixes.push(file);
    }
  }
  listingCache[key] = { fixes };
  return listingCache[key];
}

export function readListedExit(req, res) {
  const key = req.params.key;
  const data = listFixes(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
    return;
  }
  return fs.readFileSync('public/' + key + '.yml');
}

export function readListedElse(req, res) {
  const key = req.body.key;
  const data = listFixes(key);
  if (data.fixes.length === 0) {
    res.status(404).end();
  } else {
    return fs.readFileSync('public/' + key + '.yml');
  }
}
