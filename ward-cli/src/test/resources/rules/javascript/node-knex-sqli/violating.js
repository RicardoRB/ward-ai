export function handle(req, knex) {
  knex.whereRaw(req.query.clause);
}
