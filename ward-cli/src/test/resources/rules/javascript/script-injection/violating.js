export function direct(req) {
  eval(req.query.code);
}

export function propagated(req, vm) {
  const expression = req.body.expression;
  const script = 'result = ' + expression;
  vm.runInNewContext(script);
}
