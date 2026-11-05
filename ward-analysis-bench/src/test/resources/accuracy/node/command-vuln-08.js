const { exec } = require('child_process');
function h(req) {
  const job = {};
  job.cmd = req.query.cmd;
  exec(job.cmd);
}
