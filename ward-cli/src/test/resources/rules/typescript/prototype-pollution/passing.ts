export function safe(target: any, payload: any) {
  Object.assign(target, payload);
}

export function map(key: any, value: any) {
  const values = new Map();
  values.set(key, value);
  return values;
}

export const fixtures = [
  '__proto__=polluted; constructor=ctor',
  "__proto__[polluted]=yes",
  `__proto__=${1}`,
  /__proto__=a/,
];
export function parse(form: any) {
  return post(form, '__proto__=a&__proto__=b');
}
