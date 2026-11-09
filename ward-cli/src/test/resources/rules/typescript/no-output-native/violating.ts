import { Directive, EventEmitter, Output } from '@angular/core';

@Directive({ selector: '[appG]' })
export class GDirective {
  @Output() click = new EventEmitter<void>();
  @Output('blur') changed = new EventEmitter<void>();
  @Output() selected = new EventEmitter<void>();
}
