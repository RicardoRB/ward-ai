export function direct(req: any, User: any) {
  User.find(req.body);
}

export function propagated(req: any, collection: any) {
  const filter = req.query;
  collection.findOne(filter);
}
