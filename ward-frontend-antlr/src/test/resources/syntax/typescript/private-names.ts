class Counter {
  #count = 0;
  static #instances = 0;
  #label: string = "c";
  get #value(): number {
    return this.#count;
  }
  set #value(next: number) {
    this.#count = next;
  }
  #bump(): void {
    this.#count++;
  }
  static #make(): Counter {
    Counter.#instances++;
    return new Counter();
  }
  read(): number {
    this.#bump();
    return this.#value;
  }
  isCounter(value: unknown): boolean {
    return #count in value;
  }
}
