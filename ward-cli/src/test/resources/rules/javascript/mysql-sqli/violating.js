export function handle(req, connection) {
  connection.query(req.query.sql);
}
