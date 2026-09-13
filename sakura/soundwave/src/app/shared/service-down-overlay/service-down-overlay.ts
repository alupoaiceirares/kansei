import { Component, inject } from '@angular/core';
import { HealthService } from '../../core/health';

/** Full-screen blurred overlay shown app-wide whenever wirehood itself is unreachable, mounted once in app.html. */
@Component({
  selector: 'wh-service-down-overlay',
  standalone: true,
  templateUrl: './service-down-overlay.html',
  styleUrl: './service-down-overlay.css',
})
export class ServiceDownOverlayComponent {
  protected health = inject(HealthService);

  constructor() {
    this.health.ensureStarted();
  }
}
