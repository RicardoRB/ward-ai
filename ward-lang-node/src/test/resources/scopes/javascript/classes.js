import def, { a as b, c } from './m';
import * as ns from 'lib';
import 'side-effect';
export const exported = 1;
export function exportedFn() {}
export { b as renamed };
export default class Base extends def {
  static count = 0;
  field = c;
  constructor(init) { super(); this.init = init; }
  method(arg) { return ns.thing + arg + Base.count; }
}
class Other extends Missing {}
const Expr = class Named {};
