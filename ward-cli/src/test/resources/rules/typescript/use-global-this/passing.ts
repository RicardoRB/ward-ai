const w = globalThis;

export class Wrapper {
  constructor(self: object) {
    if (typeof self === 'object') {
      Object.defineProperties(this, {request: {value: self}});
    }
  }
}
