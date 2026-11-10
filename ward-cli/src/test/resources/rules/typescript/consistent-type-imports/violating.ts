import { Shape } from './shape';
import { Mark, helper } from './mark';

export function area(shape: Shape, mark: Mark): number {
  return helper(shape, mark);
}
