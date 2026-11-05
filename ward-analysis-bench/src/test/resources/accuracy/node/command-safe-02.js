const cp = require('child_process');
function h(req) {
  const n = parseInt(req.query.n, 10);
  cp.exec('kill ' + n);
}
