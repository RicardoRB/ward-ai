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
  res.setHeader('Content-Type', 'application/json');
  res.end('<script>var x = ' + JSON.stringify(req.query.x) + '</script>');
}

export function changed(req, res) {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(req.query.x).toUpperCase());
}

// JSON.stringify does not escape '<': without a JSON content type the body can be sniffed as HTML.
export function untyped(req, res) {
  res.end(JSON.stringify(req.query.x));
}

export function html(req, res) {
  res.setHeader('Content-Type', 'text/html');
  res.end(JSON.stringify(req.query.x));
}

export function otherResponse(req, res, other) {
  other.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify(req.query.x));
}

export function typedUnderCondition(req, res) {
  if (req.query.flag) {
    res.setHeader('Content-Type', 'application/json');
  }
  res.end(JSON.stringify(req.query.x));
}

export function typedAfter(req, res) {
  res.end(JSON.stringify(req.query.x));
  res.setHeader('Content-Type', 'application/json');
}
