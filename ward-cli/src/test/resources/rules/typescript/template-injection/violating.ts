export function direct(req: any, ejs: any) {
  return ejs.render(req.body.template);
}

export function propagated(req: any, handlebars: any) {
  const name = req.query.name;
  const source = '<p>Hello ' + name + '</p>';
  return handlebars.compile(source);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, ejs: any) {
  const source = read(req);
  return ejs.render(source);
}

function read(req: any) {
  return req.body.template;
}
