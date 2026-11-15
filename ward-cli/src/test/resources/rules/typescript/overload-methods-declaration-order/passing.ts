export class Shape {
  area(): number;
  area(value?: number): number {
    return value ?? 0;
  }
  perimeter(): number {
    return 0;
  }
}
export class Box {
  #width = 0;
  declare label: string;
  get width(): number {
    return this.#width;
  }
  set width(value: number) {
    this.#width = value;
  }
  get height(): number {
    return 1;
  }
}
