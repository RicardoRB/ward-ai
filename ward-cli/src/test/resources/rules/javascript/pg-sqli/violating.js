export function handle(req, client) {
  client.query('SELECT ' + req.query.id);
}
