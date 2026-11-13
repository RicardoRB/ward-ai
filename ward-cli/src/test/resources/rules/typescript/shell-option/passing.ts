import { spawn } from 'child_process';

export function run(file: any, args: any) {
  return spawn(file, args, {
    shell: false,
  });
}
