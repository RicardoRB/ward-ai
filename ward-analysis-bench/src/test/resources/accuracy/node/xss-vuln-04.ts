export function h(req: any, res: any) {
  res.send(req.query.msg);
}
