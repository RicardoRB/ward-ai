export function direct(req, res) {
  res.setHeader('X-Origin', req.query.origin);
}

export function propagated(req, response) {
  const name = req.query.name;
  const value = 'user=' + name;
  response.set('X-User', value);
}
