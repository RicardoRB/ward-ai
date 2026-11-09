export function issue(payload) {
  const token = jwt.sign(payload, 'my-secret');
  return token;
}
