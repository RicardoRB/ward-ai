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

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, db) {
  const sql = read(req);
  db.query(sql);
}

function read(req) {
  return req.query.sql;
}
