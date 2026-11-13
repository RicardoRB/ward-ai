import express from 'express';
import helmet from 'helmet';

const app = express();
app.use(helmet());
app.get('/', (req: any, res: any) => res.send('ok'));
