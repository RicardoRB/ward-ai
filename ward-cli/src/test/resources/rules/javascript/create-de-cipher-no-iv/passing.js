const crypto = require('crypto');
const c = crypto.createCipheriv('aes-128-cbc', key, iv);
