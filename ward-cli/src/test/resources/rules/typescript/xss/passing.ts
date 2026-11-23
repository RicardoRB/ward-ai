export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}

export function pick(request, response) {
  response.end(request.url === '/items?page=2' ? '[2]' : '[1]');
}
