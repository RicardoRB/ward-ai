export function used(src: number[]) {
  let first = src[0];
  console.log(first);
  [first] = src;
  return first;
}
