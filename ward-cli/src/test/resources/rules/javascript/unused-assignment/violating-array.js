export function overwritten(src) {
  let first = src[0];
  [first] = src;
  return first;
}
