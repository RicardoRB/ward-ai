export function handle(req, element) {
  const html = req.query.html;
  element.innerHTML = html;
}

export function json(req, res) {
  res.send(JSON.stringify(req.query.x));
}

export function branch(req, res) {
  res.send(req.query.flag ? req.query.x : 'none');
}

// A JSON document inside markup, or changed after serializing, is not exempt.
export function wrapped(req, res) {
  res.end('<script>var x = ' + JSON.stringify(req.query.x) + '</script>');
}

export function changed(req, res) {
  res.end(JSON.stringify(req.query.x).toUpperCase());
}
