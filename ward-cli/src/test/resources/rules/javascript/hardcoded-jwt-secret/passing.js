export function issue(payload) {
  const token = jwt.sign(payload, process.env.SECRET);
  return token;
}
