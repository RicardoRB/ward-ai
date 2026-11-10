import * as foo from "foo";
const a = foo.bar;
import { baz } from "baz";
const c = baz;
function shadow(foo) {
  return foo["bar"];
}
