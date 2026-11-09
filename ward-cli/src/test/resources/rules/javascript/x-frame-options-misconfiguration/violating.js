export function handle(req, res) {
  res.setHeader('X-Frame-Options', req.query.mode);
}
