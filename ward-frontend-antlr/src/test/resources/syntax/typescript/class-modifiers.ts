class Base {
  name = 'base';
  static kind = 0;
  protected async run(): Promise<void> {}
}

class Child extends Base {
  override name = 'child';
  declare private cache: Map<string, number>;
  private declare other: string;
  static override readonly kind = 1;
  ready!: boolean;
  override ['constructor']: typeof Child;

  protected override async run(): Promise<void> {
    this.ready = true;
  }
}

const override = 1;
console.log(override, new Child());
