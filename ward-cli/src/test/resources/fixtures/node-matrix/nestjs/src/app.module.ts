import { Module } from "@nestjs/common";
import { HealthController } from "./health.controller";

/** The root module of the fixture's Nest application. */
@Module({ controllers: [HealthController] })
export class AppModule {}
