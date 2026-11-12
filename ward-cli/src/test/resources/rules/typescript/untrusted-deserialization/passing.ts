export function json(req: any) {
  return JSON.parse(req.body.data);
}

export function constant(serialize: any) {
  return serialize.unserialize('{}');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, serialize: any) {
  const data = read(req);
  return serialize.unserialize(data);
}

function read(req: any) {
  return req.body.data;
}
