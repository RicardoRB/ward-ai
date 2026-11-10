import vm from 'vm';
export function handle(req) {
  vm.runInContext('1 + 1', vm.createContext({}));
}
