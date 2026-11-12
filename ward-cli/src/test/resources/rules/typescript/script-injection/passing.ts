export function constant() {
  eval('1 + 1');
}

export function numeric(req: any, vm: any) {
  const value = Number(req.body.value);
  vm.runInNewContext('result = ' + value);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any) {
  const code = read(req);
  eval(code);
}

function read(req: any) {
  return req.query.code;
}
