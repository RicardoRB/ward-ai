export function coerced(req: any, User: any) {
  const name = String(req.body.name);
  User.findOne(name);
}

export function constant(User: any) {
  User.find({ active: true });
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, User: any) {
  const filter = read(req);
  User.find(filter);
}

function read(req: any) {
  return req.body;
}
