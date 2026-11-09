export function handle(req, res) {
  res.set('Access-Control-Allow-Origin', req.headers.origin);
}
