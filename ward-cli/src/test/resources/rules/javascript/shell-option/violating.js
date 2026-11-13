import { spawn } from 'child_process';

export function run(command) {
  return spawn(command, [], {
    shell: true,
  });
}
