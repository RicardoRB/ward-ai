class A {
  #a = 1;
  #b = 2;
  #c = 3;
  #m() {}
  n() {
    this.#b = 5;
    this.#c++;
    return this.#m;
  }
}
