const { exec } = require('child_process');
export const handler = async (event) => {
  exec(event.body.cmd);
};
