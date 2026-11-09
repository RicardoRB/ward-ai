export function handle(req, res) {
  res.redirect(req.query.next);
}
