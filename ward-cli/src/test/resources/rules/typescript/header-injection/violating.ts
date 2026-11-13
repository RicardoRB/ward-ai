export function direct(req: any, res: any) {
  res.setHeader('X-Origin', req.query.origin);
}

export function propagated(req: any, response: any) {
  const name = req.query.name;
  const value = 'user=' + name;
  response.set('X-User', value);
}

// A value that another function of the file returns is followed.
export function crossFunction(req: any, res: any) {
  const origin = read(req);
  res.setHeader('X-Origin', origin);
}

function read(req: any) {
  return req.query.origin;
}
