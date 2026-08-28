"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import SakuraMark from "@/components/design/SakuraMark";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { PALETTE, PAGE_BG, PAGE_BG_FAINT, TEXT_SECONDARY } from "@/lib/design/palette";
import { inputStyle, primaryButtonStyle } from "@/lib/design/formStyles";
import { DISPLAY_FONT } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 1200;

export default function LinkExpiredPage() {
  const { vw } = useViewportWidth();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleResend(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await apiFetch("/api/auth/verify-email/resend", {
        method: "POST",
        body: JSON.stringify({ email }),
      });
      setSent(true);
    } finally {
      setSubmitting(false);
    }
  }

  const primaryLinkStyle = { ...primaryButtonStyle, display: "block", textAlign: "center" as const };

  return (
    <div
      style={{
        position: "relative",
        minHeight: "100vh",
        overflowX: "hidden",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <HexLatticeBackground vw={vw} height={LATTICE_HEIGHT} />

      <Header rightVariant="both" showHamburger={false} />

      <main
        style={{
          position: "relative",
          zIndex: 2,
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "72px 24px 96px",
        }}
      >
        <div
          style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            width: 900,
            height: 700,
            margin: "-350px 0 0 -450px",
            background: `radial-gradient(ellipse 60% 58% at 50% 50%, ${PAGE_BG} 0%, ${PAGE_BG} 58%, ${PAGE_BG_FAINT} 78%, transparent 100%)`,
            pointerEvents: "none",
          }}
        />

        {!sent ? (
          <div style={{ position: "relative", width: "100%", maxWidth: 520, textAlign: "center" }}>
            <SakuraMark
              size={78}
              scale={0.2}
              thickness={1.5}
              dotBase={4.5}
              opacity={0.6}
              colorForPetal={(i) => (i === 1 ? PALETTE[1] : `color-mix(in oklch, ${PALETTE[i]}, white 62%)`)}
              style={{ margin: "0 auto 26px", opacity: 0.75 }}
            />

            <div
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 9,
                padding: "7px 16px",
                borderRadius: 999,
                background: "color-mix(in oklch, oklch(48% 0.18 25) 9%, white)",
                border: "1px solid color-mix(in oklch, oklch(48% 0.18 25) 22%, white)",
                marginBottom: 22,
              }}
            >
              <span style={{ width: 7, height: 7, borderRadius: "50%", background: PALETTE[1] }} />
              <span
                style={{
                  fontWeight: 600,
                  fontSize: 11,
                  letterSpacing: 1.6,
                  textTransform: "uppercase",
                  color: "oklch(44% 0.15 25)",
                }}
              >
                Link expired
              </span>
            </div>

            <h1 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 38, margin: "0 0 16px", letterSpacing: 0.3 }}>
              This link has expired
            </h1>
            <p
              style={{
                fontSize: 15,
                lineHeight: 1.75,
                color: "oklch(48% 0.01 60)",
                margin: "0 auto 34px",
                maxWidth: 420,
              }}
            >
              Confirmation links stay valid for 24 hours. Enter your email and we&apos;ll send a fresh one — the old
              link stops working either way.
            </p>

            <form
              onSubmit={handleResend}
              noValidate
              style={{ display: "flex", flexDirection: "column", gap: 14, maxWidth: 400, margin: "0 auto" }}
            >
              <input
                type="email"
                required
                autoComplete="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="ks-input"
                style={{ ...inputStyle, textAlign: "center" }}
              />
              <button type="submit" disabled={submitting} style={primaryButtonStyle}>
                {submitting ? "Sending…" : "Resend confirmation link"}
              </button>
            </form>

            <div style={{ marginTop: 28 }}>
              <Link href="/login" style={{ fontWeight: 500, fontSize: 13, color: TEXT_SECONDARY }}>
                Back to log in
              </Link>
            </div>
          </div>
        ) : (
          <div style={{ position: "relative", width: "100%", maxWidth: 520, textAlign: "center" }}>
            <SakuraMark
              size={78}
              scale={0.2}
              thickness={1.5}
              dotBase={4.5}
              opacity={0.8}
              colorForPetal={(i) => PALETTE[i]}
              style={{ margin: "0 auto 26px" }}
            />
            <h1 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 34, margin: "0 0 14px", letterSpacing: 0.3 }}>
              New link sent
            </h1>
            <p style={{ fontSize: 15, lineHeight: 1.75, color: "oklch(48% 0.01 60)", margin: "0 auto 30px", maxWidth: 400 }}>
              Check your inbox — the new confirmation link is valid for the next 24 hours.
            </p>
            <div style={{ maxWidth: 400, margin: "0 auto" }}>
              <Link href="/" style={primaryLinkStyle}>
                Back to home
              </Link>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
