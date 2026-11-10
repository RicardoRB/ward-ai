app.use(session({ name: 'sid', secret: process.env.SECRET, cookie: { secure: true, httpOnly: true, path: '/', expires: 3600 } }));
