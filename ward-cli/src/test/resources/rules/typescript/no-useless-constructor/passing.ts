export class Props extends Base {
  constructor(private x: number) {
    super(x);
  }
}
export class Priv extends Base {
  private constructor(value: number) {
    super(value);
  }
}
export class Decorated extends Base {
  constructor(@Inject() value: number) {
    super(value);
  }
}
export class Overloaded extends Base {
  constructor(value: number);
  constructor(value: number) {
    super(value);
    this.value = value;
  }
}
