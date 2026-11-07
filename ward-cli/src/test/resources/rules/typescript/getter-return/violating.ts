class G {
  get a(): number {
    console.log(1);
  }
  get b(): number {
    return 1;
  }
  get c(): number {
    const f = () => {
      return 2;
    };
    f();
  }
}
const o = {
  get d(): number {
    console.log(2);
  },
};
