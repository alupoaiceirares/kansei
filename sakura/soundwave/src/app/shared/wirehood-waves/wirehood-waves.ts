import { Component, Input } from '@angular/core';

/**
 * Fixed, full-bleed ambient background: 5 dot-pattern wave layers, each bobbing
 * vertically on its own cycle and drifting sideways, clipped to a sine silhouette.
 * Purely decorative, sits at z-index:0 behind page content.
 *
 * `bright` picks the Landing page's bespoke higher-opacity variant (used once,
 * pre-opt-in) instead of the 5-10% ambient texture used on every other page.
 * `scoped` fills the nearest positioned ancestor instead of the viewport, Landing
 * confines the waves to the section between its header and footer, not the full page.
 */
@Component({
  selector: 'wh-waves',
  standalone: true,
  templateUrl: './wirehood-waves.html',
  styleUrl: './wirehood-waves.css',
})
export class WirehoodWavesComponent {
  @Input() bright = false;
  @Input() scoped = false;
}
