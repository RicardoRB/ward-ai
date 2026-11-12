export function constant(res) {
  res.setHeader('X-Origin', 'internal');
}

export function numeric(req, res) {
  const version = Number(req.query.version);
  res.setHeader('X-Version', 'v' + version);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, res) {
  const origin = read(req);
  res.setHeader('X-Origin', origin);
}

function read(req) {
  return req.query.origin;
}
