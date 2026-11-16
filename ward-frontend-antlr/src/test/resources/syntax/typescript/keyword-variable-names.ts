const module = await import('node:path');
const declare = module.sep;
let type = declare.length;
type += 1;
console.log(module, declare, type);
