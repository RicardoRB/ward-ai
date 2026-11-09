import { Directive, EventEmitter, Output } from '@angular/core';

@Directive({ selector: '[appI]' })
export class IDirective {
  @Output('saved') done = new EventEmitter<void>();
  @Output() ready = new EventEmitter<void>();
}
