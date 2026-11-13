export function direct(req: any, res: any) {
  res.redirect(req.query.next);
}

export function propagated(req: any, response: any) {
  const next = req.query.next;
  const target = 'https://' + next;
  response.redirect(target);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, res: any) {
  const next = read(req);
  res.redirect(next);
}

function read(req: any) {
  return req.query.next;
}
