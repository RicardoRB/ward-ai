export function json(req: any) {
  return JSON.parse(req.body.data);
}

export function constant(serialize: any) {
  return serialize.unserialize('{}');
}
