export function escaped(req) {
  return new RegExp(escapeRegExp(req.query.term));
}

export function constant() {
  return new RegExp('[a-z]+');
}
