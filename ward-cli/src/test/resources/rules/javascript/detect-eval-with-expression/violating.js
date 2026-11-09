export function handle(req) {
  setTimeout(req.query.code, 10);
}
