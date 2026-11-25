export function handle(req, element) {
  const html = req.query.html;
  element.innerHTML = html;
}

export function json(req, res) {
  res.send(JSON.stringify(req.query.x));
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
