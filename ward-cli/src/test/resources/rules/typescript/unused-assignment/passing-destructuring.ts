export function used(src: { id: number }) {
  let id = 0;
  console.log(id);
  ({ id } = src);
  return id;
}
