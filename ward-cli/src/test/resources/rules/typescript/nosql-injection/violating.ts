export function direct(req: any, User: any) {
  User.find(req.body);
}

export function propagated(req: any, collection: any) {
  const filter = req.query;
  collection.findOne(filter);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, User: any) {
  const filter = read(req);
  User.find(filter);
}

function read(req: any) {
  return req.body;
}
