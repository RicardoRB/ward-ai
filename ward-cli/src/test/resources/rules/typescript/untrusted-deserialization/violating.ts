export function direct(req: any, serialize: any) {
  return serialize.unserialize(req.body.data);
}

export function propagated(req: any, yaml: any) {
  const text = req.body.config;
  return yaml.unsafeLoad(text);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, serialize: any) {
  const data = read(req);
  return serialize.unserialize(data);
}

function read(req: any) {
  return req.body.data;
}
