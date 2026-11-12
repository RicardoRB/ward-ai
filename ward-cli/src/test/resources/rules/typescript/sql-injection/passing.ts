export function parameterized(req: any, connection: any) {
  connection.query('SELECT * FROM users WHERE id = ?', [req.params.id]);
}

export function numeric(req: any, connection: any) {
  const id = Number(req.params.id);
  connection.query('SELECT * FROM users WHERE id = ' + id);
}

export function constant(db: any) {
  db.query('SELECT 1');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, db: any) {
  const sql = read(req);
  db.query(sql);
}

function read(req: any) {
  return req.query.sql;
}
