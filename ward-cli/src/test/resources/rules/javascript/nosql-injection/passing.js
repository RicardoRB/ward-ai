export function coerced(req, User) {
  const name = String(req.body.name);
  User.findOne(name);
}

export function constant(User) {
  User.find({ active: true });
}
