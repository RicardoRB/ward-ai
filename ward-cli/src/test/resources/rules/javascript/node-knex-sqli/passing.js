export function handle(req, knex) {
  knex.whereRaw(parseInt(req.query.clause));
}
