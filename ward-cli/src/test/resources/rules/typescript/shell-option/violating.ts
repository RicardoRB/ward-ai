import { spawn } from 'child_process';

export function run(command: any) {
  return spawn(command, [], {
    shell: true,
  });
}
