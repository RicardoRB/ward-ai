class S {
  set value(x) {
    this.v = x;
    return x;
  }
  set other(x) {
    const f = () => {
      return 1;
    };
    f();
  }
}
const o = {
  set a(x) {
    return;
  },
  set b(x) {
    return 1;
  },
};
