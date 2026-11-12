export function direct(req, User) {
  User.find(req.body);
}

export function propagated(req, collection) {
  const filter = req.query;
  collection.findOne(filter);
}
