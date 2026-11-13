export function parameterized(req, connection) {
  connection.query('SELECT * FROM users WHERE id = ?', [req.params.id]);
}

export function numeric(req, connection) {
  const id = Number(req.params.id);
  connection.query('SELECT * FROM users WHERE id = ' + id);
}

export function constant(db) {
  db.query('SELECT 1');
}
