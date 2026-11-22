export function handle(req, element) {
  const html = req.query.html;
  element.innerHTML = html;
}

export function json(req, res) {
  res.send(JSON.stringify(req.query.x));
}
