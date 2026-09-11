import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CONTROL_TOWER_URL } from './config';

/** Thin wrapper over control-tower's /wirehood/** — auth header is attached by authInterceptor. */
@Injectable({ providedIn: 'root' })
export class WirehoodApi {
  private http = inject(HttpClient);

  optIn() {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/users/opt-in`, {});
  }
}
