const https = require('https');
const agent = https.request({ host: 'example.com', rejectUnauthorized: false });
