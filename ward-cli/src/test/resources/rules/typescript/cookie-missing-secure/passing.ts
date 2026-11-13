export function login(res: any) {
  res.cookie('id', '1', { secure: true });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { secure: 'auto' } });
}

export function forwarded(res: any, options: any) {
  res.cookie('id', '1', options);
}
