export function handle(req, res) {
  res.send(req.query.name);
}
