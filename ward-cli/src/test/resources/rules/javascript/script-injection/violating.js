export function direct(req) {
  eval(req.query.code);
}

export function propagated(req, vm) {
  const expression = req.body.expression;
  const script = 'result = ' + expression;
  vm.runInNewContext(script);
}

// A value that another function of the file returns is followed.
export function crossFunction(req) {
  const code = read(req);
  eval(code);
}

function read(req) {
  return req.query.code;
}
