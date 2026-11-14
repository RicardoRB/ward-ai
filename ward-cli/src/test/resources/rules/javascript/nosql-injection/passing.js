export function coerced(req, User) {
  const name = String(req.body.name);
  User.findOne(name);
}

export function constant(User) {
  User.find({ active: true });
}

export function chainedConstant(db) {
  db.ordersCollection.find({ active: true });
}
