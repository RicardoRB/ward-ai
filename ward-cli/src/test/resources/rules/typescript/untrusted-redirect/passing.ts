export function constant(res: any) {
  res.redirect('/home');
}

export function numeric(req: any, res: any) {
  const page = Number(req.query.page);
  res.redirect('/page/' + page);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, res: any) {
  const next = read(req);
  res.redirect(next);
}

function read(req: any) {
  return req.query.next;
}
