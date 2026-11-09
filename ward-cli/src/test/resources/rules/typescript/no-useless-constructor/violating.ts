export class Base {
  constructor() {}
}
export class Child extends Base {
  constructor(value: number) {
    super(value);
  }
}
export class Rest extends Base {
  constructor(...args: number[]) {
    super(...args);
  }
}
