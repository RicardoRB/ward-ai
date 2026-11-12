export function direct(req) {
  return new RegExp(req.query.pattern);
}

export function propagated(req, text) {
  const term = req.query.term;
  const expression = '.*' + term + '.*';
  return new RegExp(expression).test(text);
}
