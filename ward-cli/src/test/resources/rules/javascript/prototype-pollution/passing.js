export function safe(target, payload) {
  Object.assign(target, payload);
}

export function map(key, value) {
  const values = new Map();
  values.set(key, value);
  return values;
}
