export function direct(req, res) {
  res.setHeader('X-Origin', req.query.origin);
}

export function propagated(req, response) {
  const name = req.query.name;
  const value = 'user=' + name;
  response.set('X-User', value);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, res) {
  const origin = read(req);
  res.setHeader('X-Origin', origin);
}

function read(req) {
  return req.query.origin;
}
