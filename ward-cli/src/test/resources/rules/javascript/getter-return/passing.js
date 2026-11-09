const o = {
  get a() {
    return 1;
  },
};
class C {
  get c() {
    return this.x;
  }
  set c(v) {
    this.x = v;
  }
}
