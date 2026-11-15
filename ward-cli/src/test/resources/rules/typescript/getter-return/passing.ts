class C {
  get c(): number {
    return 1;
  }
  set c(v: number) {
    void v;
  }
}
export type Shape = {
  get side(): number;
  set side(value: number);
};
export declare class Ambient {
  get side(): number;
}
