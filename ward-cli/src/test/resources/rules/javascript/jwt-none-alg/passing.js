const jwt = require('jsonwebtoken');
const payload = jwt.verify(token, key, { algorithms: ['HS256'] });
