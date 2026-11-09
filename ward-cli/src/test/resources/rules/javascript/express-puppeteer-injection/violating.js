export function handle(req, page) {
  page.goto(req.query.url);
}
