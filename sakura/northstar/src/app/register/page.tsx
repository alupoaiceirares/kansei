"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import Banner from "@/components/design/Banner";
import Field from "@/components/design/Field";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { extractErrorMessage, extractFieldErrors } from "@/lib/errors";
import { CARD_BORDER, CARD_SHADOW, PAGE_BG, TEXT_SECONDARY } from "@/lib/design/palette";
import { primaryButtonStyle, secondaryLinkStyle } from "@/lib/design/formStyles";
import { authCardH1, authCardSubline } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 1200;

export default function RegisterPage() {
  const { vw } = useViewportWidth();

  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setFieldErrors({});
    setSubmitting(true);

    try {
      const res = await apiFetch("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          email,
          username,
          password,
          firstName: firstName || undefined,
          lastName: lastName || undefined,
        }),
      });
      const body = await res.json().catch(() => null);

      if (!res.ok) {
        setError(extractErrorMessage(body, "Registration failed."));
        setFieldErrors(extractFieldErrors(body));
        return;
      }

      // No token here - register only returns { message }, unlike login
      // Verification/login happens as a separate step
      setMessage(body?.message ?? "Registered. Check your email to verify your account.");
    } finally {
      setSubmitting(false);
    }
  }

  const cx = vw / 2;
  const skip = (hx: number, hy: number) => Math.hypot(hx - cx, hy - 580) < 340;

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

      <Header rightVariant="loginOnly" />

      <main
        style={{
          position: "relative",
          zIndex: 2,
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "64px 24px 88px",
        }}
      >
        <div
          style={{
            position: "absolute",
            left: "50%",
            top: "50%",
            width: 840,
            height: 760,
            margin: "-380px 0 0 -420px",
            background: `radial-gradient(ellipse closest-side, ${PAGE_BG} 0%, ${PAGE_BG} 66%, transparent 100%)`,
            pointerEvents: "none",
          }}
        />

        <div
          style={{
            position: "relative",
            width: "100%",
            maxWidth: 480,
            background: "#fff",
            border: `1px solid ${CARD_BORDER}`,
            borderRadius: 20,
            boxShadow: CARD_SHADOW,
            padding: "44px 40px 36px",
          }}
        >
          <h1 style={authCardH1}>Create your account</h1>
          <p style={{ ...authCardSubline, color: TEXT_SECONDARY }}>Join The Kansei Project.</p>

          <form onSubmit={handleSubmit} noValidate>
            <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
              <Field
                label="Email"
                type="email"
                autoComplete="email"
                maxLength={255}
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                error={fieldErrors.email}
              />
              <Field
                label="Username"
                type="text"
                autoComplete="username"
                maxLength={40}
                placeholder="aiko.t"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                error={fieldErrors.username}
              />
              <Field
                label="Password"
                type="password"
                autoComplete="new-password"
                maxLength={72}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                error={fieldErrors.password}
              />
              <div className="ks-register-names" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <Field
                  label={
                    <>
                      First name{" "}
                      <span style={{ textTransform: "none", letterSpacing: 0, color: "oklch(62% 0.01 60)" }}>
                        (optional)
                      </span>
                    </>
                  }
                  type="text"
                  autoComplete="given-name"
                  maxLength={100}
                  placeholder="Aiko"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  error={fieldErrors.firstName}
                />
                <Field
                  label={
                    <>
                      Last name{" "}
                      <span style={{ textTransform: "none", letterSpacing: 0, color: "oklch(62% 0.01 60)" }}>
                        (optional)
                      </span>
                    </>
                  }
                  type="text"
                  autoComplete="family-name"
                  maxLength={100}
                  placeholder="Tanaka"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  error={fieldErrors.lastName}
                />
              </div>
            </div>

            {error && (
              <Banner variant="error" style={{ margin: "18px 0 0" }}>
                {error}
              </Banner>
            )}
            {message && (
              <Banner variant="success" style={{ margin: "18px 0 0" }}>
                {message}
              </Banner>
            )}

            <div style={{ margin: "28px 0 0" }}>
              <button type="submit" disabled={submitting} style={primaryButtonStyle}>
                {submitting ? "Registering…" : "Register"}
              </button>
            </div>
          </form>

          <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "26px 0 20px" }}>
            <span style={{ flex: 1, height: 1, background: "rgba(0,0,0,0.08)" }} />
            <span style={{ fontSize: 12, color: "oklch(60% 0.01 60)" }}>or</span>
            <span style={{ flex: 1, height: 1, background: "rgba(0,0,0,0.08)" }} />
          </div>

          <Link href="/login" style={secondaryLinkStyle}>
            Already have an account? Log in
          </Link>
        </div>
      </main>
    </div>
  );
}
