export function json(req) {
  return JSON.parse(req.body.data);
}

export function constant(serialize) {
  return serialize.unserialize('{}');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, serialize) {
  const data = read(req);
  return serialize.unserialize(data);
}

function read(req) {
  return req.body.data;
}
