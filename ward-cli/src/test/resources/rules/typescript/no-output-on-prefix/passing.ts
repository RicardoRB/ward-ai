import { Directive, EventEmitter, Output } from '@angular/core';

@Directive({ selector: '[appH]' })
export class HDirective {
  @Output() changed = new EventEmitter<void>();
  @Output('one') done = new EventEmitter<void>();
  @Output() online = new EventEmitter<void>();
}
