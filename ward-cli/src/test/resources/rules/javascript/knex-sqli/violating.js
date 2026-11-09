export function handle(req, knex) {
  knex.raw(req.query.sql);
}
