export function data(req, ejs) {
  return ejs.render('<p>Hello <%= name %></p>', { name: req.query.name });
}

export function constant(res) {
  res.render('home');
}
