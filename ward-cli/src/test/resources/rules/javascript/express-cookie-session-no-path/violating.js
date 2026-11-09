app.use(session({ name: 'sid', secret: process.env.SECRET, cookie: { secure: true, httpOnly: true, domain: 'example.com', expires: 3600 } }));
