export function handle(req) {
  const code = req.query.code;
  eval(code);
}
