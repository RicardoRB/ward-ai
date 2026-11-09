import { Component, Directive } from '@angular/core';

@Directive({
  selector: '[appE]',
  hostDirectives: [{ directive: Other, inputs: ['x'] }],
})
export class EDirective {}

@Component({ selector: 'app-e', template: '' })
export class EComponent {
  @Input() name = '';
}
