const serialize = require('node-serialize');
export function handle(req) {
  serialize.unserialize(req.body.data);
}
