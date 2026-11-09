import pug from 'pug';
export function handle(req) {
  const fn = pug.compile('p ready');
  return fn({});
}
