export function login(res) {
  res.cookie('id', '1', { domain: 'app.example.com', path: '/app' });
}

export function plain(res) {
  res.cookie('id', '1');
}
