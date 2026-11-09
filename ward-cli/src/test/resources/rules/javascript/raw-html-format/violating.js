import util from 'util';
export function handle(req, res) {
  res.end(util.format('<p>%s</p>', req.query.name));
}
