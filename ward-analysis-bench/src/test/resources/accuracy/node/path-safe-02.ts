import path from 'path';
import * as fs from 'fs';
export function h(req: any) {
  fs.readFile(path.basename(req.params.name), () => {});
}
