export function coerced(req, User) {
  const name = String(req.body.name);
  User.findOne(name);
}

export function constant(User) {
  User.find({ active: true });
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, User) {
  const filter = read(req);
  User.find(filter);
}

function read(req) {
  return req.body;
}
