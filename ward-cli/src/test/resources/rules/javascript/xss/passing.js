export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}

// A plain-text response that browsers may not sniff cannot run a reflected value as markup.
function setFinalHeaders(res) {
  res.setHeader('Content-Type', 'text/plain; charset=utf-8');
  res.setHeader('X-Content-Type-Options', 'nosniff');
}

export function viaHelper(req, res) {
  setFinalHeaders(res);
  res.end(`Cannot ${req.method} ${req.url}`);
}

export function direct(req, res) {
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.send(req.query.name);
}

export function head(req, res) {
  res.writeHead(404, {'Content-Type': 'text/plain', 'X-Content-Type-Options': 'nosniff'});
  res.end(req.query.name);
}
