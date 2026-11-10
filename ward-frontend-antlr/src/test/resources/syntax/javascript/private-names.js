class Counter {
  #count = 0;
  static #instances = 0;
  get #value() {
    return this.#count;
  }
  set #value(next) {
    this.#count = next;
  }
  #bump() {
    this.#count++;
  }
  static #make() {
    Counter.#instances++;
    return new Counter();
  }
  read() {
    this.#bump();
    return this.#value;
  }
  isCounter(value) {
    return #count in value;
  }
}
