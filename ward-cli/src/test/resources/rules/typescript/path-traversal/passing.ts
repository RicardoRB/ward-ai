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

// A value that a list of constants accepted is one of those constants.
const KIND_LIST = ['avatar', 'logo'];

export function ternaryAllowList(req) {
  const url = req.body.imageUrl;
  const ext = ['jpg', 'png'].includes(url.split('.').slice(-1)[0]) ? url.split('.').slice(-1)[0] : 'jpg';
  fs.writeFileSync(`uploads/avatar.${ext}`, 'x');
}

export function insideAllowList(req) {
  const kind = req.query.kind;
  if (KIND_LIST.includes(kind)) {
    return fs.readFileSync('images/' + kind);
  }
  return undefined;
}

export function pastAllowList(req) {
  const kind = req.query.kind;
  if (!KIND_LIST.includes(kind)) {
    throw new Error('unknown kind');
  }
  return fs.readFileSync('images/' + kind);
}

// A function that throws unless its parameter is on the list vouches for its callers.
function requireKind(value) {
  if (!KIND_LIST.includes(value)) {
    throw new Error('unknown kind');
  }
}

export function validatedByCallee(req) {
  const kind = req.query.kind;
  requireKind(kind);
  return fs.readFileSync('images/' + kind);
}

export function defaultedConstant(req) {
  const file = req.query.file ? 'index.html' : 'about.html';
  return fs.readFileSync('public/' + file);
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
