export function handle(req, element) {
  const html = req.query.html;
  element.textContent = html;
}

export function pick(request, response) {
  response.end(request.url === '/items?page=2' ? '[2]' : '[1]');
}

// A JSON document written after the same response was given a JSON content type is data.
export function typedHeader(request, response) {
  response.setHeader('Content-Type', 'application/json');
  response.end(JSON.stringify({headers: request.headers}));
}

export function typedHead(req, res) {
  res.writeHead(200, {'Content-Type': 'application/json; charset=utf-8'});
  res.write(JSON.stringify([req.url]));
  res.end(JSON.stringify(req.query.page));
}

export function typedType(req, res) {
  res.type('json');
  res.end(JSON.stringify(req.query.x));
}

export function typedBeforeBranch(req, res) {
  res.header('content-type', 'application/vnd.api+json');
  if (req.query.flag) {
    res.end(JSON.stringify(req.query.x));
  } else {
    res.write(JSON.stringify(req.query.y));
  }
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

export function shortType(req, res) {
  res.type('text');
  res.header('x-content-type-options', 'nosniff');
  res.send(req.query.name);
}
