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

export function chainedConstant(models: any) {
  models.sequelize.query('SELECT 1');
}
