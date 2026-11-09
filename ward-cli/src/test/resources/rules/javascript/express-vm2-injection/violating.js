import { VM } from 'vm2';
export function handle(req) {
  const vm = new VM();
  vm.run(req.body.code);
}
