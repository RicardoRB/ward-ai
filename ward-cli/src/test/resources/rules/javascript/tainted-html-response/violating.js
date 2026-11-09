export function handle(req, res) {
  res.write(req.query.html);
}
