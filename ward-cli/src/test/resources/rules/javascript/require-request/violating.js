export function handle(req) {
  const plugin = require(req.query.module);
  return plugin;
}
