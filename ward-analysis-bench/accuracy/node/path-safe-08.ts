import path from 'path';
export function h(req: any, res: any) {
  res.sendFile(path.resolve(__dirname, 'public', 'x.html'));
}
