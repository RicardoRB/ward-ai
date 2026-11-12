export function direct(req: any, serialize: any) {
  return serialize.unserialize(req.body.data);
}

export function propagated(req: any, yaml: any) {
  const text = req.body.config;
  return yaml.unsafeLoad(text);
}
