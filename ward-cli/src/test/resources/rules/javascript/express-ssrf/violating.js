import request from 'request';
export function handle(req) {
  request.get(req.query.url);
}
