const fs = require('fs');
function h(req, res) {
  fs.readFile(req.params.name, () => {});
}
