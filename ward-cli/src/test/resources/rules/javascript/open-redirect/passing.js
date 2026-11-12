export function constant(res) {
  res.redirect('/home');
}

export function numeric(req, res) {
  const page = Number(req.query.page);
  res.redirect('/page/' + page);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, res) {
  const next = read(req);
  res.redirect(next);
}

function read(req) {
  return req.query.next;
}
