const crypto = require('crypto');
function decrypt(key, iv, data) {
  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);
  const out = d.update(data);
  d.final();
  return out;
}
