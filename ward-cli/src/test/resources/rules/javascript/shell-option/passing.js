import { spawn } from 'child_process';

export function run(file, args) {
  return spawn(file, args, {
    shell: false,
  });
}
