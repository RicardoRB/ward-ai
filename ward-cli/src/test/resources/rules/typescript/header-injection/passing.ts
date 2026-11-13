export function constant(res: any) {
  res.setHeader('X-Origin', 'internal');
}

export function numeric(req: any, res: any) {
  const version = Number(req.query.version);
  res.setHeader('X-Version', 'v' + version);
}
