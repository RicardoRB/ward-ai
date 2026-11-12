export function direct(req: any) {
  return new RegExp(req.query.pattern);
}

export function propagated(req: any, text: any) {
  const term = req.query.term;
  const expression = '.*' + term + '.*';
  return new RegExp(expression).test(text);
}
