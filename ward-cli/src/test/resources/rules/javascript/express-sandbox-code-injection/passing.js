const Sandbox = require('sandbox');
export function handle(req) {
  const s = new Sandbox();
  s.run('1 + 1');
}
