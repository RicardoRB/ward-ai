class G {
  get a() {
    console.log(1);
  }
  get b() {
    return 1;
  }
  get c() {
    const f = () => {
      return 2;
    };
    f();
  }
}
const o = {
  get d() {
    console.log(2);
  },
};
