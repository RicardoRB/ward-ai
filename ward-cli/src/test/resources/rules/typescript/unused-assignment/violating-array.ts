export function overwritten(src: number[]) {
  let first = src[0];
  [first] = src;
  return first;
}
