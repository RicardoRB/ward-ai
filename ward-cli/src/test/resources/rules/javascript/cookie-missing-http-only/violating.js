export function login(res) {
  res.cookie('id', '1');
}

export function withOptions(res) {
  res.cookie('id', '1', { secure: true });
}
