export class Shape {
  area() {
    return 0;
  }
  area() {
    return 1;
  }
  perimeter() {
    return 0;
  }
}
export class Box {
  #width = 0;
  get width() {
    return this.#width;
  }
  set width(value) {
    this.#width = value;
  }
  get height() {
    return 1;
  }
}
