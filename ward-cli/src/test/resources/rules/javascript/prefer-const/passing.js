export function total(values) {
  let count = values.length;
  count += 1;
  let late;
  if (values.length) {
    late = 1;
  }
  return count + (late || 0);
}
