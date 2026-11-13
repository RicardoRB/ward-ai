export function direct(req, ejs) {
  return ejs.render(req.body.template);
}

export function propagated(req, handlebars) {
  const name = req.query.name;
  const source = '<p>Hello ' + name + '</p>';
  return handlebars.compile(source);
}

// A value that another function of the file returns is followed.
export function crossFunction(req, ejs) {
  const source = read(req);
  return ejs.render(source);
}

function read(req) {
  return req.body.template;
}
