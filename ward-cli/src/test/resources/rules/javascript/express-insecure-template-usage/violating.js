import pug from 'pug';
export function handle(req) {
  const fn = pug.compile(req.query.template);
  return fn({});
}
