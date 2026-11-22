export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}

export function json(req, res) {
  res.end(JSON.stringify({headers: req.headers}));
}
