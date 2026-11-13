export function direct(req: any, db: any) {
  db.query(req.query.sql);
}

export function propagated(req: any, connection: any) {
  const id = req.params.id;
  const sql = 'SELECT * FROM users WHERE id = ' + id;
  connection.query(sql);
}

export function template(req: any, pool: any) {
  const name = req.body.name;
  pool.execute(`SELECT * FROM users WHERE name = '${name}'`);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, db: any) {
  const sql = read(req);
  db.query(sql);
}

function read(req: any) {
  return req.query.sql;
}
