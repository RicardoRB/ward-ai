import express from 'express';
import helmet from 'helmet';

const app = express();
app.use(helmet());
app.get('/', (req, res) => res.send('ok'));
