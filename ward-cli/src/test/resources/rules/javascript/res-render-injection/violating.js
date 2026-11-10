export function handle(req, res) {
  res.render(req.query.view);
}
