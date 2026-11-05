export function h(req: any) {
  return new Function(req.query.body);
}
