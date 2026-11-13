export function login(res: any) {
  res.cookie('id', '1', { domain: '.example.com' });
}

export function rooted(res: any) {
  res.cookie('id', '1', { path: '/' });
}
