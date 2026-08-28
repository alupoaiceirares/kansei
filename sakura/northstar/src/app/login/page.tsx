"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import Banner from "@/components/design/Banner";
import Field from "@/components/design/Field";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { setToken } from "@/lib/auth";
import { extractErrorMessage, extractFieldErrors } from "@/lib/errors";
import { CARD_BORDER, CARD_SHADOW, FOCUS_COLOR, MODAL_SHADOW, PAGE_BG, TEXT_SECONDARY } from "@/lib/design/palette";
import { fieldLabelStyle, inputStyle, primaryButtonStyle, secondaryLinkStyle } from "@/lib/design/formStyles";
import { authCardH1, authCardSubline, modalH2 } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 1100;

export default function LoginPage() {
  const router = useRouter();
  const { vw } = useViewportWidth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const [resetOpen, setResetOpen] = useState(false);
  const [resetEmail, setResetEmail] = useState("");
  const [resetSent, setResetSent] = useState(false);
  const [resetSubmitting, setResetSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      const res = await apiFetch("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      const body = await res.json().catch(() => null);

      if (!res.ok) {
        setError(extractErrorMessage(body, "Login failed."));
        setFieldErrors(extractFieldErrors(body));
        return;
      }

      setToken(body.token);
      router.push("/");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResetSubmit(e: FormEvent) {
    e.preventDefault();
    setResetSubmitting(true);
    try {
      await apiFetch("/api/auth/password-reset/request", {
        method: "POST",
        body: JSON.stringify({ email: resetEmail }),
      });
      // Deliberately generic regardless of outcome.. don't reveal whether the address exists
      setResetSent(true);
    } finally {
      setResetSubmitting(false);
    }
  }

  function closeReset() {
    setResetOpen(false);
    setResetSent(false);
    setResetEmail("");
  }

  const cx = vw / 2;
  const skip = (hx: number, hy: number) => Math.hypot(hx - cx, hy - 520) < 300;

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
      <HexLatticeBackground vw={vw} height={LATTICE_HEIGHT} skip={skip} />

      <Header rightVariant="registerOnly" />

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
            width: 760,
            height: 620,
            margin: "-310px 0 0 -380px",
            background: `radial-gradient(ellipse closest-side, ${PAGE_BG} 0%, ${PAGE_BG} 66%, transparent 100%)`,
            pointerEvents: "none",
          }}
        />

        <div
          style={{
            position: "relative",
            width: "100%",
            maxWidth: 420,
            background: "#fff",
            border: `1px solid ${CARD_BORDER}`,
            borderRadius: 20,
            boxShadow: CARD_SHADOW,
            padding: "44px 40px 36px",
          }}
        >
          <h1 style={authCardH1}>Welcome back</h1>
          <p style={{ ...authCardSubline, color: TEXT_SECONDARY }}>Log in to continue to The Kansei Project.</p>

          <form onSubmit={handleSubmit} noValidate>
            <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
              <Field
                label="Email"
                type="email"
                autoComplete="username"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                error={fieldErrors.email}
              />
              <Field
                label="Password"
                type="password"
                autoComplete="current-password"
                maxLength={72}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                error={fieldErrors.password}
              />
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", margin: "14px 0 26px" }}>
              <button
                type="button"
                onClick={() => setResetOpen(true)}
                style={{
                  fontWeight: 500,
                  fontSize: 13,
                  border: "none",
                  background: "transparent",
                  color: FOCUS_COLOR,
                  cursor: "pointer",
                  padding: 0,
                }}
              >
                Forgot password?
              </button>
            </div>

            {error && (
              <Banner variant="error" style={{ margin: "0 0 16px" }}>
                {error}
              </Banner>
            )}

            <button type="submit" disabled={submitting} style={primaryButtonStyle}>
              {submitting ? "Logging in…" : "Log in"}
            </button>
          </form>

          <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "26px 0 20px" }}>
            <span style={{ flex: 1, height: 1, background: "rgba(0,0,0,0.08)" }} />
            <span style={{ fontSize: 12, color: "oklch(60% 0.01 60)" }}>or</span>
            <span style={{ flex: 1, height: 1, background: "rgba(0,0,0,0.08)" }} />
          </div>

          <Link href="/register" style={secondaryLinkStyle}>
            You don&apos;t have an account yet?
          </Link>
        </div>
      </main>

      {resetOpen && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 50,
            background: "oklch(30% 0.02 60 / 0.35)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 24,
          }}
        >
          <div
            style={{
              width: "100%",
              maxWidth: 400,
              background: "#fff",
              borderRadius: 20,
              boxShadow: MODAL_SHADOW,
              padding: "36px 34px 30px",
            }}
          >
            {!resetSent ? (
              <form onSubmit={handleResetSubmit} noValidate>
                <h2 style={modalH2}>Reset your password</h2>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: TEXT_SECONDARY, margin: "0 0 24px" }}>
                  Enter the email tied to your account and we&apos;ll send a reset link.
                </p>
                <label style={{ display: "flex", flexDirection: "column", gap: 7, marginBottom: 24 }}>
                  <span style={fieldLabelStyle}>Email</span>
                  <input
                    type="email"
                    required
                    autoComplete="email"
                    placeholder="you@example.com"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                    className="ks-input"
                    style={inputStyle}
                  />
                </label>
                <button type="submit" disabled={resetSubmitting} style={primaryButtonStyle}>
                  {resetSubmitting ? "Sending…" : "Send reset link"}
                </button>
                <button
                  type="button"
                  onClick={closeReset}
                  style={{
                    width: "100%",
                    marginTop: 12,
                    fontWeight: 500,
                    fontSize: 13,
                    border: "none",
                    background: "transparent",
                    color: TEXT_SECONDARY,
                    cursor: "pointer",
                    padding: "10px 0",
                  }}
                >
                  Cancel
                </button>
              </form>
            ) : (
              <div style={{ textAlign: "center" }}>
                <h2 style={modalH2}>Check your email</h2>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: TEXT_SECONDARY, margin: "0 0 24px" }}>
                  If an account exists for that address, a reset link is on its way.
                </p>
                <button type="button" onClick={closeReset} style={primaryButtonStyle}>
                  Done
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
