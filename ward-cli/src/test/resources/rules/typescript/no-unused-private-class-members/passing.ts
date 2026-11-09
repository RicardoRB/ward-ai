class A {
  #a = 1;
  static #s = 2;
  get #g() { return this.#a; }
  n() { return A.#s + this.#g; }
}
