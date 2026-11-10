const serveIndex = require('serve-index');
app.use(serveIndex('public', { icons: true }));
