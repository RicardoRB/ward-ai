export function handle(req, res) {
  res.sendFile(req.query.file);
}
