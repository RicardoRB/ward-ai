export function direct(req: any, ejs: any) {
  return ejs.render(req.body.template);
}

export function propagated(req: any, handlebars: any) {
  const name = req.query.name;
  const source = '<p>Hello ' + name + '</p>';
  return handlebars.compile(source);
}
