export function constant() {
  eval('1 + 1');
}

export function numeric(req: any, vm: any) {
  const value = Number(req.body.value);
  vm.runInNewContext('result = ' + value);
}
