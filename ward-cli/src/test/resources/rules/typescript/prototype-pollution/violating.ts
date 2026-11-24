export function pollute(target: any, payload: any) {
  target.__proto__ = payload;
}

export function nested(target: any) {
  target['__proto__']['admin'] = true;
}

export function dynamicKey(target: any, key: string, value: any) {
  target[key]['__proto__'] = value;
  target.constructor.prototype.admin = value;
}
