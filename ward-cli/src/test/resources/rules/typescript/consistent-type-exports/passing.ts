import type { Shape } from './shape';

interface Point {
  x: number;
}

type Name = string;

const count = 1;

export type { Point };
export { count, type Name };
export { helper } from './helper';
export type { Shape as Form };
