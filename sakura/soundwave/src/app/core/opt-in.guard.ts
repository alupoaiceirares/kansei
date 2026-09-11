import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';

/** Gates every real app page behind the opt-in modal until this browser has joined wirehood. */
export const optInGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isOptedIn()) return true;
  return inject(Router).parseUrl('/opt-in');
};
