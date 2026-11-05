import { exec } from 'child_process';
export function h(req) {
  exec('uptime');
}
