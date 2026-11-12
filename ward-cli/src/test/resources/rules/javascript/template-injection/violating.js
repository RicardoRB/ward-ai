export function direct(req, ejs) {
  return ejs.render(req.body.template);
}

export function propagated(req, handlebars) {
  const name = req.query.name;
  const source = '<p>Hello ' + name + '</p>';
  return handlebars.compile(source);
}
