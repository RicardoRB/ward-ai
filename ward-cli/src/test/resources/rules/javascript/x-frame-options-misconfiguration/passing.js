export function handle(req, res) {
  res.setHeader('X-Frame-Options', 'DENY');
}
