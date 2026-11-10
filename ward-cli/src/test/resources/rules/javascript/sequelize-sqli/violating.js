export function handle(req, sequelize) {
  sequelize.query(req.query.sql);
}
