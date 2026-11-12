export function constant(res: any) {
  res.setHeader('X-Origin', 'internal');
}

export function numeric(req: any, res: any) {
  const version = Number(req.query.version);
  res.setHeader('X-Version', 'v' + version);
}

// The limit: a value that leaves the function is not followed.
export function crossFunction(req: any, res: any) {
  const origin = read(req);
  res.setHeader('X-Origin', origin);
}

function read(req: any) {
  return req.query.origin;
}
