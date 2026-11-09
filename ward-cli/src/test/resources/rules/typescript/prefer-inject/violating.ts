import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class JService {
  constructor(
    private http: HttpClient,
    logger: Logger,
    name: string,
  ) {}
}
