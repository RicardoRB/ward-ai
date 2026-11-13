export function direct(req, res) {
  res.redirect(req.query.next);
}

export function propagated(req, response) {
  const next = req.query.next;
  const target = 'https://' + next;
  response.redirect(target);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, res) {
  const next = read(req);
  res.redirect(next);
}

function read(req) {
  return req.query.next;
}
