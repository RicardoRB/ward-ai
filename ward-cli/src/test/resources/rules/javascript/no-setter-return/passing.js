class S {
  set value(x) {
    this.v = x;
  }
  set other(x) {
    const f = () => {
      return 1;
    };
    f();
  }
  get value() {
    return this.v;
  }
}
const o = {
  set a(x) {
    return;
  },
};
