export function login(res: any) {
  res.cookie('id', '1');
}

export function withOptions(res: any) {
  res.cookie('id', '1', { secure: true });
}
