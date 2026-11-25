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

// Plain text without nosniff can still be sniffed as HTML.
export function plainOnly(req, res) {
  res.setHeader('Content-Type', 'text/plain');
  res.send(req.query.name);
}

// nosniff does not help when the type is HTML, is not a literal, or is changed afterwards.
export function htmlNosniff(req, res) {
  res.setHeader('Content-Type', 'text/html');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.send(req.query.name);
}

export function userType(req, res) {
  res.setHeader('Content-Type', req.query.type);
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.send(req.query.name);
}

export function retyped(req, res) {
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Content-Type', 'text/html');
  res.send(req.query.name);
}

export function unsniffed(req, res) {
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.removeHeader('X-Content-Type-Options');
  res.send(req.query.name);
}

export function conditional(req, res) {
  if (req.query.flag) {
    res.setHeader('Content-Type', 'text/plain');
    res.setHeader('X-Content-Type-Options', 'nosniff');
  }
  res.send(req.query.name);
}

export function writtenFirst(req, res) {
  res.send(req.query.name);
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('X-Content-Type-Options', 'nosniff');
}

export function anotherResponse(req, res, other) {
  other.setHeader('Content-Type', 'text/plain');
  other.setHeader('X-Content-Type-Options', 'nosniff');
  res.send(req.query.name);
}

// A helper counts only for the response it is given, and only when it sets both on every path.
function setTextType(res) {
  res.setHeader('Content-Type', 'text/plain');
}

function setFinalMaybe(res, flag) {
  if (flag) {
    res.setHeader('Content-Type', 'text/plain');
    res.setHeader('X-Content-Type-Options', 'nosniff');
  }
}

function setBoth(res) {
  res.setHeader('Content-Type', 'text/plain');
  res.setHeader('X-Content-Type-Options', 'nosniff');
}

export function helperWithoutNosniff(req, res) {
  setTextType(res);
  res.send(req.query.name);
}

export function helperOnCondition(req, res) {
  setFinalMaybe(res, req.query.flag);
  res.send(req.query.name);
}

export function helperForAnother(req, res, other) {
  setBoth(other);
  res.send(req.query.name);
}

export function headWithUserType(req, res) {
  res.writeHead(200, {'Content-Type': req.query.type, 'X-Content-Type-Options': 'nosniff'});
  res.end(req.query.name);
}

export function shortHtml(req, res) {
  res.type('html');
  res.header('X-Content-Type-Options', 'nosniff');
  res.send(req.query.name);
}
