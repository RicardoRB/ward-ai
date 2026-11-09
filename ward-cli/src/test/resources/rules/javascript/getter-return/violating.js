const o = {
  get a() {
    console.log(1);
  },
  get b() {
    return;
  },
};
class C {
  get c() {}
}
