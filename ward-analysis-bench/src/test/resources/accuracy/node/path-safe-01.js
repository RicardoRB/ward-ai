const fs = require('fs');
const path = require('path');
function h(base) {
  fs.readFile(path.join(base, 'config.json'), () => {});
}
