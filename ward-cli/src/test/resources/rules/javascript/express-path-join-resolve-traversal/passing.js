import path from 'path';
export function handle(req) {
  return path.join('/files', 'readme.txt');
}
