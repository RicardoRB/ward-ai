export function overwritten(src) {
  let id = src.id;
  ({ id } = src);
  return id;
}
