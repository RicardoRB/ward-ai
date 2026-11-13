export function direct(req, serialize) {
  return serialize.unserialize(req.body.data);
}

export function propagated(req, yaml) {
  const text = req.body.config;
  return yaml.unsafeLoad(text);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, serialize) {
  const data = read(req);
  return serialize.unserialize(data);
}

function read(req) {
  return req.body.data;
}
