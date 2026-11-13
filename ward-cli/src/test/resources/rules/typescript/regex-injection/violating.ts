export function direct(req: any) {
  return new RegExp(req.query.pattern);
}

export function propagated(req: any, text: any) {
  const term = req.query.term;
  const expression = '.*' + term + '.*';
  return new RegExp(expression).test(text);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any) {
  const pattern = read(req);
  return new RegExp(pattern);
}

function read(req: any) {
  return req.query.pattern;
}
