import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MiniPlayerComponent } from './shared/mini-player/mini-player';
import { PlaybackService } from './core/playback';

@Component({
  imports: [RouterOutlet, MiniPlayerComponent],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected playback = inject(PlaybackService);
}
