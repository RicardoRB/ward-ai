const cp = require('child_process');
function handle(req, res) {
  const cmd = 'tar -czf out.tgz ' + req.body.dir;
  cp.execSync(cmd);
}
