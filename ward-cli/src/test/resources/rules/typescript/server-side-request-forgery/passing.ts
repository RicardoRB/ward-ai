export function constant() {
  return fetch('https://api.example.com/status');
}

export function numeric(req: any) {
  const id = Number(req.query.id);
  return fetch('https://api.example.com/items/' + id);
}
