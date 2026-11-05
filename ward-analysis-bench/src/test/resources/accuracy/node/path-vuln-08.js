const fs = require('fs');
function h(req) {
  const o = {};
  o.name = req.params.name;
  fs.unlink(o.name, () => {});
}
