class S {
  private v = 0;
  set value(x: number) {
    this.v = x;
    return x;
  }
  set other(x: number) {
    const f = () => {
      return 1;
    };
    f();
  }
}
const o = {
  set a(x: number) {
    return;
  },
  set b(x: number) {
    return 1;
  },
};
