export function constant(res: any) {
  res.redirect('/home');
}

export function numeric(req: any, res: any) {
  const page = Number(req.query.page);
  res.redirect('/page/' + page);
}
