import { Component, Input } from '@angular/core';

@Component({ selector: 'app-c', template: '' })
export class CComponent {
  @Input() label = '';
  @Input('aria-label') ariaLabel = '';
  @Input() size = 0;
}
