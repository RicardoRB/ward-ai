export function direct(req, db) {
  db.query(req.query.sql);
}

export function propagated(req, connection) {
  const id = req.params.id;
  const sql = 'SELECT * FROM users WHERE id = ' + id;
  connection.query(sql);
}

export function template(req, pool) {
  const name = req.body.name;
  pool.execute(`SELECT * FROM users WHERE name = '${name}'`);
}
