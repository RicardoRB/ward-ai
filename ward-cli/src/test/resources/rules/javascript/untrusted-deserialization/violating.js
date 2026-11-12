export function direct(req, serialize) {
  return serialize.unserialize(req.body.data);
}

export function propagated(req, yaml) {
  const text = req.body.config;
  return yaml.unsafeLoad(text);
}
