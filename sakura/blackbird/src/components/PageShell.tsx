import type { ReactNode } from 'react';
import { COLORS, FONT_STACK, PAGE_MAX_WIDTH, PAGE_PADDING_X } from '../design/tokens';
import { BlackbirdNav } from './BlackbirdNav';
import { Footer } from './Footer';
import { PlaneBackdrop } from './PlaneBackdrop';

type Props = {
  children: ReactNode;
  /** Recap runs narrower than the rest of the app. */
  maxWidth?: number;
  /** The map page spans the full width instead of the capped column. */
  fullBleed?: boolean;
  nav?: boolean;
};

// The shell every page is built on: fixed flight-path backdrop, nav, capped content column, footer.
export function PageShell({ children, maxWidth = PAGE_MAX_WIDTH, fullBleed = false, nav = true }: Props) {
  return (
    <div
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        minHeight: '100vh',
        background: COLORS.ground,
        fontFamily: FONT_STACK,
        color: COLORS.text,
      }}
    >
      <PlaneBackdrop />
      {nav && <BlackbirdNav />}
      <main
        style={{
          position: 'relative',
          flex: '1 0 auto',
          width: '100%',
          maxWidth: fullBleed ? '100%' : maxWidth,
          margin: '0 auto',
          padding: `36px ${PAGE_PADDING_X}px 72px`,
          display: 'flex',
          flexDirection: 'column',
          gap: 22,
        }}
      >
        {children}
      </main>
      <Footer />
    </div>
  );
}
