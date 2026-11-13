export function login(res: any) {
  res.cookie('id', '1', { httpOnly: true });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { secure: true } });
}

export function forwarded(res: any, options: any) {
  res.cookie('id', '1', options);
}
