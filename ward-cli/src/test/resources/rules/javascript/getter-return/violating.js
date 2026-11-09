const o = {
  get a() {
    console.log(1);
  },
  get b() {
    return;
  },
  get d() {
    if (c) {
      return 1;
    }
  },
};
class C {
  get c() {}
}
