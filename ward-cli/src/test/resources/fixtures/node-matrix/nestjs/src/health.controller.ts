import { Controller, Get } from "@nestjs/common";
import { type Observable, of } from "rxjs";

/** Reports that the service is up. */
@Controller("health")
export class HealthController {
  @Get()
  check(): Observable< { status: string } > {
    return of({ status: "ok" });
  }
}
