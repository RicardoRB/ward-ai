export function pollute(target: any, payload: any) {
  target.__proto__ = payload;
}

export function nested(target: any) {
  target['__proto__']['admin'] = true;
}
