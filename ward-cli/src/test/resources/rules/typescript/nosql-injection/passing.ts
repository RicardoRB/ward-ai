export function coerced(req: any, User: any) {
  const name = String(req.body.name);
  User.findOne(name);
}

export function constant(User: any) {
  User.find({ active: true });
}
