import { Component, Input } from '@angular/core';

@Component({ selector: 'app-c', template: '' })
export class CComponent {
  @Input('title') heading = '';
}
