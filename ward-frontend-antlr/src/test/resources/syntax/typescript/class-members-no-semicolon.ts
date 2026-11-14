class Box {
  public label?: string
  private count!: number
  readonly size = 1
  items: string[] = []
  [key: string]: unknown
  declare note?: string
  declare static shared: number

  static async load(): Promise<Box> {
    return new Box()
  }

  private async save(): Promise<void> {
    await this.persist()
  }

  async * stream(): AsyncGenerator<number> {
    yield 1
  }

  * values() {
    yield 2
  }

  get total(): number {
    return this.size
  }

  set total(value: number) {
    this.count = value
  }

  overloaded(a: number): void
  overloaded(a: string): void
  overloaded(a: any): void {}
}
