export function handle(req, page) {
  page.evaluate(req.query.script);
}
