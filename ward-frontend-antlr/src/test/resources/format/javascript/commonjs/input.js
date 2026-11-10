const path = require('path');
const { readFile } = require("fs");
function load(name){
  return readFile(path.join(__dirname, name));
}
module.exports = { load, version: 1 };
exports.helper = function helper() { return 2 };
