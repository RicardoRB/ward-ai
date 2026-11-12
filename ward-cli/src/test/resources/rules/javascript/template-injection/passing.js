export function data(req, ejs) {
  return ejs.render('<p>Hello <%= name %></p>', { name: req.query.name });
}

export function constant(res) {
  res.render('home');
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req, ejs) {
  const source = read(req);
  return ejs.render(source);
}

function read(req) {
  return req.body.template;
}
