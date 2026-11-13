export function login(res: any) {
  res.cookie('id', '1', { httpOnly: false });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { secure: false } });
}
