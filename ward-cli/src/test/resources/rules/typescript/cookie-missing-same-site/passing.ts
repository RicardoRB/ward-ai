export function login(res: any) {
  res.cookie('id', '1', { sameSite: 'lax' });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { sameSite: 'strict' } });
}

export function forwarded(res: any, options: any) {
  res.cookie('id', '1', options);
}
