export function h(req, res) {
  res.download(req.query.name);
}
