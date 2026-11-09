export function handle(req, sequelize) {
  sequelize.query(parseInt(req.query.id));
}
