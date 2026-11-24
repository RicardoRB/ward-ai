export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}

export function pick(request, response) {
  response.end(request.url === '/items?page=2' ? '[2]' : '[1]');
}

// The body of a raw response write that is one JSON document is data, not markup.
export function jsonBody(request, response) {
  response.end(JSON.stringify({headers: request.headers}));
}

export function jsonList(req, res) {
  res.write(JSON.stringify([req.url]));
  res.end(JSON.stringify(req.query.page));
}
