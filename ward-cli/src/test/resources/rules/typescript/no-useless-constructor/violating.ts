export class Base {
  constructor() {}
}

export class Child extends Base {
  constructor(...args) {
    super(...args);
  }
}
