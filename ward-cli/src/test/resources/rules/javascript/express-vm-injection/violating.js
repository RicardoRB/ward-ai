import vm from 'vm';
export function handle(req) {
  vm.runInContext(req.body.code, vm.createContext({}));
}
