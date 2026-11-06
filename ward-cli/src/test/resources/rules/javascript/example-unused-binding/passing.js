function work(path, extra) {
  const { a, b } = path;
  let total = 0;
  for (const item of extra) {
    total += item;
  }
  try {
    total += a;
  } catch (error) {
    total += 1;
  }
  const _unused = 1;
  return total + b;
}
