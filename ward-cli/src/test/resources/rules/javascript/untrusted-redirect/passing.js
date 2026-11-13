export function constant(res) {
  res.redirect('/home');
}

export function numeric(req, res) {
  const page = Number(req.query.page);
  res.redirect('/page/' + page);
}
