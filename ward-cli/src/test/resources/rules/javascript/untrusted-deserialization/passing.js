export function json(req) {
  return JSON.parse(req.body.data);
}

export function constant(serialize) {
  return serialize.unserialize('{}');
}
