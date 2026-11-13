export function constant(res) {
  res.setHeader('X-Origin', 'internal');
}

export function numeric(req, res) {
  const version = Number(req.query.version);
  res.setHeader('X-Version', 'v' + version);
}
