export function safe(target: any, payload: any) {
  Object.assign(target, payload);
}

export function map(key: any, value: any) {
  const values = new Map();
  values.set(key, value);
  return values;
}
