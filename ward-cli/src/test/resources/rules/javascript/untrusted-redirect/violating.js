export function direct(req, res) {
  res.redirect(req.query.next);
}

export function propagated(req, response) {
  const next = req.query.next;
  const target = 'https://' + next;
  response.redirect(target);
}
