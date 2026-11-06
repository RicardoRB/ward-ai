function work(path: string, extra: number[]): number {
  const [first] = extra;
  let total = 0;
  for (const item of extra) {
    total += item;
  }
  return total + first + path.length;
}
