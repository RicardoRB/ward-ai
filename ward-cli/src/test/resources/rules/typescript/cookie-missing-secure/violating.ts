export function login(res: any) {
  res.cookie('id', '1');
}

export function withOptions(res: any) {
  res.cookie('id', '1', { httpOnly: true });
}

export function sessions(session: any) {
  return session({ secret: 'x', cookie: { httpOnly: true } });
}
