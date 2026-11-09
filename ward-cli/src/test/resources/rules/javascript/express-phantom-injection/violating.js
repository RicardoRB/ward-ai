export function handle(req, page) {
  page.setContent(req.body.html);
}
