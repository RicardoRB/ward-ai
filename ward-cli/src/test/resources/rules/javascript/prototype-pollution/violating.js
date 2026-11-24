export function pollute(target, payload) {
  target.__proto__ = payload;
}

export function nested(target) {
  target['__proto__']['admin'] = true;
}

export function dynamicKey(target, key, value) {
  target[key]['__proto__'] = value;
  target.constructor.prototype.admin = value;
}
