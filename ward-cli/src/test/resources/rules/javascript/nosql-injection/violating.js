export function direct(req, User) {
  User.find(req.body);
}

export function propagated(req, collection) {
  const filter = req.query;
  collection.findOne(filter);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, User) {
  const filter = read(req);
  User.find(filter);
}

function read(req) {
  return req.body;
}
