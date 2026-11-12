export function data(req: any, ejs: any) {
  return ejs.render('<p>Hello <%= name %></p>', { name: req.query.name });
}

export function constant(res: any) {
  res.render('home');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, ejs: any) {
  const source = read(req);
  return ejs.render(source);
}

function read(req: any) {
  return req.body.template;
}
