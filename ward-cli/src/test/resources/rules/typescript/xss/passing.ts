export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}
