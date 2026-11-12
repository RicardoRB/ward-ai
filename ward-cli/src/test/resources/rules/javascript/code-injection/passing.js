export function constant() {
  eval('1 + 1');
}

export function numeric(req, vm) {
  const value = Number(req.body.value);
  vm.runInNewContext('result = ' + value);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req) {
  const code = read(req);
  eval(code);
}

function read(req) {
  return req.query.code;
}
