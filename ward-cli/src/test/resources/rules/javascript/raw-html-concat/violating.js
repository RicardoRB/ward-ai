export function handle(req, res) {
  res.send('<p>' + req.query.name + '</p>');
}
