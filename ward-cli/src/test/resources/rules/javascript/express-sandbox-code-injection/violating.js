const Sandbox = require('sandbox');
export function handle(req) {
  const s = new Sandbox();
  s.run(req.query.code);
}
