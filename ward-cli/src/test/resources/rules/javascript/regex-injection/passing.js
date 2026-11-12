export function escaped(req) {
  return new RegExp(escapeRegExp(req.query.term));
}

export function constant() {
  return new RegExp('[a-z]+');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req) {
  const pattern = read(req);
  return new RegExp(pattern);
}

function read(req) {
  return req.query.pattern;
}
