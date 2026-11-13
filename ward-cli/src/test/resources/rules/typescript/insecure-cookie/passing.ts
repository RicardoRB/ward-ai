export function login(res: any) {
  res.cookie('id', '1', { httpOnly: true, secure: true });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { secure: true, httpOnly: true } });
}
