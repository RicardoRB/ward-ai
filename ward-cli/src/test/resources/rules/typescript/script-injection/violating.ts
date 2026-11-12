export function direct(req: any) {
  eval(req.query.code);
}

export function propagated(req: any, vm: any) {
  const expression = req.body.expression;
  const script = 'result = ' + expression;
  vm.runInNewContext(script);
}
