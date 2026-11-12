export function pollute(target, payload) {
  target.__proto__ = payload;
}

export function nested(target) {
  target['__proto__']['admin'] = true;
}
