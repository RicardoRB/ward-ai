import * as fsp from 'fs/promises';
export async function h(req: any) {
  await fsp.readFile(req.query.file);
}
