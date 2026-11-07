class S {
  private v = 0;
  set value(x: number) {
    this.v = x;
  }
  set other(x: number) {
    const f = () => {
      return 1;
    };
    f();
  }
  get value(): number {
    return this.v;
  }
}
const o = {
  set a(x: number) {
    return;
  },
};
