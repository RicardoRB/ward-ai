const jwt = require('jwt-simple');
const payload = jwt.decode(token, key, true);
