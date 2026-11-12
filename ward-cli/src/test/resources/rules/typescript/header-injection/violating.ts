export function direct(req: any, res: any) {
  res.setHeader('X-Origin', req.query.origin);
}

export function propagated(req: any, response: any) {
  const name = req.query.name;
  const value = 'user=' + name;
  response.set('X-User', value);
}
