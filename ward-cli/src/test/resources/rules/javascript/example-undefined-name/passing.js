import { helper } from './helper';

const limit = 3;
var hoisted = later();

function later() {
  return limit + helper(hoisted);
}

class Box {
  size = limit;
  grow(step) {
    return [step, this.size].map((n) => n + step);
  }
}
console.log(Box, window.name, process.env);
