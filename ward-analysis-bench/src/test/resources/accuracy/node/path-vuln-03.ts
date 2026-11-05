export function h(req: any, res: any) {
  res.sendFile(req.params.p);
}
