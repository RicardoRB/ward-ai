app.use(session({ name: 'sid', secret: process.env.SECRET, cookie: { secure: true, httpOnly: true, path: '/', domain: 'example.com', expires: 3600 } }));
