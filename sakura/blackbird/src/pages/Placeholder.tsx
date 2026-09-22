import { PageShell } from '../components/PageShell';
import { COLORS, overline, pageTitle } from '../design/tokens';

/** Temporary stand-in for screens that arrive in a later build chunk, replaced as each lands. */
export function Placeholder({ title }: { title: string }) {
  return (
    <PageShell>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <span style={overline}>WTW</span>
        <h1 style={pageTitle}>{title}</h1>
        <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted }}>Not built yet.</p>
      </div>
    </PageShell>
  );
}
