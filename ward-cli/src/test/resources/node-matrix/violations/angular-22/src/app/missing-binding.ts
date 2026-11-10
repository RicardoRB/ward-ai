import { Component } from '@angular/core';

@Component({
  selector: 'app-missing-binding',
  template: '<p>{{ missingProperty }}</p>',
})
export class MissingBinding {}
