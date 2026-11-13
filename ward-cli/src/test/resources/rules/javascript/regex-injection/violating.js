export function direct(req) {
  return new RegExp(req.query.pattern);
}

export function propagated(req, text) {
  const term = req.query.term;
  const expression = '.*' + term + '.*';
  return new RegExp(expression).test(text);
}

// A value that another function of the file returns is followed.
export function crossFunction(req) {
  const pattern = read(req);
  return new RegExp(pattern);
}

function read(req) {
  return req.query.pattern;
}
