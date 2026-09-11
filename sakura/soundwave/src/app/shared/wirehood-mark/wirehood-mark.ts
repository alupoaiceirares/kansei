import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild, computed, signal } from '@angular/core';

/**
 * Animated "wirehood" wordmark: green cable arc draws in under/through the two
 * "o"s on mount, and replays on hover. Self-measures glyph positions so the SVG
 * overlay stays aligned to the live-rendered text regardless of font-loading timing.
 */
@Component({
  selector: 'wh-mark',
  standalone: true,
  templateUrl: './wirehood-mark.html',
  styleUrl: './wirehood-mark.css',
})
export class WirehoodMarkComponent implements AfterViewInit, OnDestroy {
  @Input() scale = 0.62;

  @ViewChild('wrapEl') private wrapElRef?: ElementRef<HTMLDivElement>;
  @ViewChild('wordEl') private wordElRef?: ElementRef<HTMLSpanElement>;
  @ViewChild('ooEl') private ooElRef?: ElementRef<HTMLSpanElement>;
  @ViewChild('markEl') private markElRef?: ElementRef<HTMLSpanElement>;

  protected svgVisible = signal(true);
  protected leftRingX = signal(189);
  protected rightRingX = signal(215);
  protected ringCy = signal(36);
  protected letterTop = signal(25);
  protected letterBottom = signal(47);
  protected wordLeft = signal(121);

  private readonly jackX = 253;

  protected boxW = computed(() => (316 * this.scale).toFixed(1) + 'px');
  protected boxH = computed(() => (90 * this.scale).toFixed(1) + 'px');

  protected bandD = computed(() => {
    const lx = this.leftRingX();
    const rx = this.rightRingX();
    const top = this.letterTop();
    const bandRxE = (rx - lx) / 2;
    const bandRyE = bandRxE * 0.9;
    return `M${lx} ${top} A ${bandRxE} ${bandRyE} 0 0 1 ${rx} ${top}`;
  });

  private midY = computed(() => this.letterBottom() + 15.5);

  protected cableLeftD = computed(() => {
    const lx = this.leftRingX();
    const bottom = this.letterBottom();
    const midY = this.midY();
    const startX = this.wordLeft() + 2.6;
    return `M${startX} ${bottom} C ${startX} ${bottom + 9}, ${startX + 3.9} ${midY}, ${startX + 11.6} ${midY} L${lx - 10} ${midY} C ${lx - 5} ${midY}, ${lx} ${bottom + 9}, ${lx} ${bottom}`;
  });

  protected cableRightD = computed(() => {
    const rx = this.rightRingX();
    const bottom = this.letterBottom();
    const midY = this.midY();
    return `M${rx} ${bottom} C ${rx} ${bottom + 9}, ${rx + 5} ${midY}, ${rx + 10} ${midY} L${this.jackX} ${midY}`;
  });

  protected jackCollarX = this.jackX;
  protected jackShaftX = this.jackX + 4.5;
  protected jackBandX = this.jackX + 10.3;
  protected jackTipX = this.jackX + 14.8;
  protected jackCollarY = computed(() => +(this.midY() - 4.5).toFixed(1));
  protected jackShaftY = computed(() => +(this.midY() - 2.6).toFixed(2));
  protected jackTipY = computed(() => +(this.midY() - 2.3).toFixed(1));

  private readonly resizeHandler = () => this.measure();

  protected onEnter(): void {
    // Force-remount the SVG (like React's key bump) so the CSS draw-in animation replays.
    this.svgVisible.set(false);
    requestAnimationFrame(() => this.svgVisible.set(true));
  }

  ngAfterViewInit(): void {
    requestAnimationFrame(() => {
      this.measure();
      requestAnimationFrame(() => this.measure());
    });
    window.addEventListener('resize', this.resizeHandler);
    document.fonts?.ready?.then(() => this.measure());
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.resizeHandler);
  }

  private measure(): void {
    const wrapEl = this.wrapElRef?.nativeElement;
    const ooEl = this.ooElRef?.nativeElement;
    const markEl = this.markElRef?.nativeElement;
    const wordEl = this.wordElRef?.nativeElement;
    if (!wrapEl || !ooEl || !markEl) return;

    const wrapRect = wrapEl.getBoundingClientRect();
    const ooRect = ooEl.getBoundingClientRect();
    if (!wrapRect.width || !ooRect.height) return;

    const s = this.scale;
    const ooLeft = (ooRect.left - wrapRect.left) / s;
    const cs = getComputedStyle(ooEl);
    const ls = parseFloat(cs.letterSpacing) || 0;
    const oAdv = (ooRect.width / s - 2 * ls) / 2;

    const mkRect = markEl.getBoundingClientRect();
    const baseline = (mkRect.bottom - wrapRect.top) / s;
    const xHeight = mkRect.height / s;
    const overshoot = parseFloat(cs.fontSize) * 0.015;
    const glyphTop = baseline - xHeight - overshoot;
    const glyphBottom = baseline + overshoot;

    const wordLeft = wordEl ? (wordEl.getBoundingClientRect().left - wrapRect.left) / s : 121;

    this.wordLeft.set(wordLeft);
    this.leftRingX.set(ooLeft + oAdv / 2);
    this.rightRingX.set(ooLeft + oAdv * 1.5 + ls);
    this.ringCy.set((glyphTop + glyphBottom) / 2);
    this.letterTop.set(glyphTop);
    this.letterBottom.set(glyphBottom);
  }
}
