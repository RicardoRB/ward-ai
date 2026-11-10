export function used(src) {
  let first = src[0];
  console.log(first);
  [first] = src;
  return first;
}
