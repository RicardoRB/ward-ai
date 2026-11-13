export function direct(req: any) {
  eval(req.query.code);
}

export function propagated(req: any, vm: any) {
  const expression = req.body.expression;
  const script = 'result = ' + expression;
  vm.runInNewContext(script);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any) {
  const code = read(req);
  eval(code);
}

function read(req: any) {
  return req.query.code;
}
