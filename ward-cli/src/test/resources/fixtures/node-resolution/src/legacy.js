const { table } = require("./data");
const name = "lazy";
const lazy = import("./lazy.js");
const dynamicRequire = require(name);
const template = import(`./${name}.js`);

module.exports.legacy = table;
