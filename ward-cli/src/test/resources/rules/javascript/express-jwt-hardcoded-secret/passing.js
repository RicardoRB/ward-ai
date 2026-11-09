app.use(jwt({ secret: process.env.SECRET, algorithms: ['HS256'] }));
