export function overwritten(src: { id: number }) {
  let id = src.id;
  ({ id } = src);
  return id;
}
