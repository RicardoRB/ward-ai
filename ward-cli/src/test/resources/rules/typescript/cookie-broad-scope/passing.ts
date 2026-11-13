export function login(res: any) {
  res.cookie('id', '1', { domain: 'app.example.com', path: '/app' });
}

export function plain(res: any) {
  res.cookie('id', '1');
}
