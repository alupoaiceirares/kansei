"use client";

import { Suspense, useEffect, useRef, useState, type FormEvent } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import SakuraMark from "@/components/design/SakuraMark";
import Banner from "@/components/design/Banner";
import Field from "@/components/design/Field";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { extractErrorMessage, extractFieldErrors } from "@/lib/errors";
import { CARD_BORDER, CARD_SHADOW, PAGE_BG, PALETTE, TEXT_SECONDARY } from "@/lib/design/palette";
import { primaryButtonStyle } from "@/lib/design/formStyles";
import { authCardH1, authCardSubline, DISPLAY_FONT } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 1100;
const REDIRECT_DELAY_MS = 2200;

function ResetPasswordInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { vw } = useViewportWidth();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const redirectRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (redirectRef.current) clearTimeout(redirectRef.current);
    };
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldErrors({});

    if (newPassword !== confirmPassword) {
      setError("Passwords don't match.");
      setFieldErrors({ confirmPassword: "Doesn't match the new password above." });
      return;
    }
    if (!token) {
      setError("Missing reset token.");
      return;
    }

    setSubmitting(true);
    try {
      const res = await apiFetch("/api/auth/password-reset/confirm", {
        method: "POST",
        body: JSON.stringify({ token, newPassword }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(extractErrorMessage(body, "That reset link is invalid or has expired."));
        setFieldErrors(extractFieldErrors(body));
        return;
      }

      setDone(true);
      redirectRef.current = setTimeout(() => {
        router.push("/");
      }, REDIRECT_DELAY_MS);
    } finally {
      setSubmitting(false);
    }
  }

  const cx = vw / 2;
  const skip = (hx: number, hy: number) => Math.hypot(hx - cx, hy - 520) < 300;

  return (
    <div style={{ position: "relative", minHeight: "100vh", overflowX: "hidden", display: "flex", flexDirection: "column" }}>
      <HexLatticeBackground vw={vw} height={LATTICE_HEIGHT} skip={skip} />

      <Header rightVariant="none" showHamburger={false} />

      <main style={{ position: "relative", zIndex: 2, flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "72px 24px 96px" }}>
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
          {!done ? (
            <form onSubmit={handleSubmit} noValidate>
              <h1 style={authCardH1}>Set a new password</h1>
              <p style={{ ...authCardSubline, color: TEXT_SECONDARY }}>
                Choose a new password for your account. Use at least 8 characters.
              </p>

              <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                <Field
                  label="New password"
                  type="password"
                  autoComplete="new-password"
                  maxLength={72}
                  placeholder="••••••••"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  error={fieldErrors.newPassword}
                />
                <Field
                  label="Confirm new password"
                  type="password"
                  autoComplete="new-password"
                  maxLength={72}
                  placeholder="••••••••"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  error={fieldErrors.confirmPassword}
                />
              </div>

              {error && (
                <Banner variant="error" style={{ margin: "18px 0 0" }}>
                  {error}
                </Banner>
              )}

              <div style={{ margin: "28px 0 0" }}>
                <button type="submit" disabled={submitting} style={primaryButtonStyle}>
                  {submitting ? "Confirming…" : "Confirm new password"}
                </button>
              </div>

              <div style={{ display: "flex", justifyContent: "center", marginTop: 14 }}>
                <Link href="/login" style={{ fontWeight: 500, fontSize: 13, color: TEXT_SECONDARY }}>
                  Back to log in
                </Link>
              </div>
            </form>
          ) : (
            <div style={{ textAlign: "center", padding: "8px 0 4px" }}>
              <SakuraMark
                size={64}
                scale={0.16}
                thickness={1.5}
                dotBase={4}
                colorForPetal={(i) => PALETTE[i]}
                style={{ margin: "0 auto 22px" }}
              />
              <h1 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 27, margin: "0 0 10px", letterSpacing: 0.3 }}>
                Password updated
              </h1>
              <p style={{ fontSize: 14, lineHeight: 1.6, color: TEXT_SECONDARY, margin: "0 0 28px" }}>
                You&apos;re all set. Taking you back to The Kansei Project…
              </p>
              <Link href="/" style={{ ...primaryButtonStyle, display: "block", textAlign: "center" }}>
                Continue to home
              </Link>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordInner />
    </Suspense>
  );
}
