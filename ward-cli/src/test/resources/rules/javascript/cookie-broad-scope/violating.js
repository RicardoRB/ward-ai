export function login(res) {
  res.cookie('id', '1', { domain: '.example.com' });
}

export function rooted(res) {
  res.cookie('id', '1', { path: '/' });
}
