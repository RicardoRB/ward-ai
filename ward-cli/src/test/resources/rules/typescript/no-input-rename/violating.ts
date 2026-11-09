import { Component, Input } from '@angular/core';

@Component({ selector: 'app-c', template: '' })
export class CComponent {
  @Input('title') heading = '';
  @Input({ alias: 'size' }) size = 0;
  @Input() label = '';
}
