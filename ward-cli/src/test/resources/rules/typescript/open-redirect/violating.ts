export function direct(req: any, res: any) {
  res.redirect(req.query.next);
}

export function propagated(req: any, response: any) {
  const next = req.query.next;
  const target = 'https://' + next;
  response.redirect(target);
}
