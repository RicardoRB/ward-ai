import { exec } from 'child_process';
import { Request, Response } from 'express';
export function handle(req: Request, res: Response) {
  const host = req.query.host;
  exec(`ping -c 1 ${host}`);
}
