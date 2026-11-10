import { Component, Directive } from '@angular/core';

@Directive({
  selector: '[appF]',
  hostDirectives: [{ directive: Other, outputs: ['x'] }],
})
export class FDirective {}

@Component({ selector: 'app-f', template: '' })
export class FComponent {
  @Output() change = new EventEmitter<void>();
}
