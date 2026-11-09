import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class JService {
  constructor(
    private http: HttpClient,
    @Optional() logger: Logger,
    name: string,
  ) {}
}

export class Plain {
  constructor(logger: Logger) {}
}
