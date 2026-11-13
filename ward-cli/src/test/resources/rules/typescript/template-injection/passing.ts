export function data(req: any, ejs: any) {
  return ejs.render('<p>Hello <%= name %></p>', { name: req.query.name });
}

export function constant(res: any) {
  res.render('home');
}
