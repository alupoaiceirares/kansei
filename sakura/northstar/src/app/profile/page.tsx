"use client";

import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import SakuraMark from "@/components/design/SakuraMark";
import SakuraSpinner from "@/components/design/SakuraSpinner";
import Banner from "@/components/design/Banner";
import PasswordCapableInput, { FIELD_ERROR_COLOR } from "@/components/design/PasswordCapableInput";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { clearToken, getToken } from "@/lib/auth";
import { extractErrorMessage, extractFieldErrors } from "@/lib/errors";
import { PAGE_BG, PAGE_BG_FAINT, PALETTE, TEXT_SECONDARY } from "@/lib/design/palette";
import { fieldLabelStyle, inputStyleWhite, primaryButtonStyle } from "@/lib/design/formStyles";
import { DISPLAY_FONT } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 2200;

type Section = "profile" | "password" | "deactivate";
const SECTIONS: { key: Section; label: string; color: string }[] = [
  { key: "profile", label: "Edit profile", color: PALETTE[0] },
  { key: "password", label: "Change password", color: PALETTE[2] },
  { key: "deactivate", label: "Deactivate account", color: PALETTE[1] },
];

const rowBase = {
  display: "grid",
  gridTemplateColumns: "190px minmax(0, 1fr)",
  gap: 28,
  padding: "18px 0",
} as const;

function FieldRow({
  label,
  children,
  error,
  last = false,
}: {
  label: string;
  children: ReactNode;
  error?: string;
  last?: boolean;
}) {
  return (
    <div
      style={{
        ...rowBase,
        alignItems: error ? "start" : "center",
        borderBottom: last ? undefined : "1px solid rgba(0,0,0,0.08)",
      }}
    >
      <span style={fieldLabelStyle}>{label}</span>
      <div>
        {children}
        {error && <span style={{ display: "block", marginTop: 6, fontSize: 12, color: FIELD_ERROR_COLOR }}>{error}</span>}
      </div>
    </div>
  );
}

export default function ProfilePage() {
  const router = useRouter();
  const { vw } = useViewportWidth();
  const [section, setSection] = useState<Section>("profile");
  const [loaded, setLoaded] = useState(false);

  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [profileFieldErrors, setProfileFieldErrors] = useState<Record<string, string>>({});

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [passwordFieldErrors, setPasswordFieldErrors] = useState<Record<string, string>>({});

  const [deactivatePassword, setDeactivatePassword] = useState("");
  const [deactivating, setDeactivating] = useState(false);
  const [deactivateError, setDeactivateError] = useState<string | null>(null);
  const [deactivateFieldErrors, setDeactivateFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }

    let cancelled = false;
    apiFetch("/api/auth/me")
      .then(async (res) => {
        if (cancelled) return;
        if (res.status === 401) {
          router.replace("/login");
          return;
        }
        if (!res.ok) return;
        const body = await res.json();
        if (cancelled) return;
        setEmail(body.email ?? "");
        setUsername(body.username ?? "");
        setFirstName(body.firstName ?? "");
        setLastName(body.lastName ?? "");
        setLoaded(true);
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [router]);

  async function handleProfileSubmit(e: FormEvent) {
    e.preventDefault();
    setProfileError(null);
    setProfileMessage(null);
    setProfileFieldErrors({});
    setProfileSaving(true);
    try {
      const res = await apiFetch("/api/auth/me", {
        method: "PATCH",
        body: JSON.stringify({ email, username, firstName, lastName }),
      });
      if (res.status === 401) {
        router.replace("/login");
        return;
      }
      const body = await res.json().catch(() => null);
      if (!res.ok) {
        setProfileError(extractErrorMessage(body, "Couldn't save your changes."));
        setProfileFieldErrors(extractFieldErrors(body));
        return;
      }
      setProfileMessage("Changes saved.");
    } finally {
      setProfileSaving(false);
    }
  }

  async function handlePasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setPasswordError(null);
    setPasswordMessage(null);
    setPasswordFieldErrors({});

    if (newPassword !== confirmPassword) {
      setPasswordError("New passwords don't match.");
      setPasswordFieldErrors({ confirmPassword: "Doesn't match the new password above." });
      return;
    }

    setPasswordSaving(true);
    try {
      const res = await apiFetch("/api/auth/me/password", {
        method: "PUT",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      if (res.status === 401) {
        router.replace("/login");
        return;
      }
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setPasswordError(extractErrorMessage(body, "Couldn't change your password."));
        setPasswordFieldErrors(extractFieldErrors(body));
        return;
      }
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setPasswordMessage("Password changed.");
    } finally {
      setPasswordSaving(false);
    }
  }

  async function handleDeactivateSubmit(e: FormEvent) {
    e.preventDefault();
    setDeactivateError(null);
    setDeactivateFieldErrors({});
    setDeactivating(true);
    try {
      const res = await apiFetch("/api/auth/me/deactivate", {
        method: "POST",
        body: JSON.stringify({ currentPassword: deactivatePassword }),
      });
      if (res.status === 401) {
        router.replace("/login");
        return;
      }
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setDeactivateError(extractErrorMessage(body, "Couldn't deactivate your account."));
        setDeactivateFieldErrors(extractFieldErrors(body));
        return;
      }
      clearToken();
      router.push("/");
    } finally {
      setDeactivating(false);
    }
  }

  return (
    <div style={{ position: "relative", minHeight: "100vh", overflowX: "hidden", display: "flex", flexDirection: "column" }}>
      <HexLatticeBackground vw={vw} height={LATTICE_HEIGHT} />

      <Header rightVariant="auto" highlightProfile />

      <main style={{ position: "relative", zIndex: 2, flex: 1, padding: "66px 40px 120px" }}>
        <div style={{ position: "relative", maxWidth: 1040, margin: "0 auto" }}>
          {!loaded ? (
            <div style={{ display: "flex", justifyContent: "center", padding: "120px 0" }}>
              <SakuraSpinner size={56} />
            </div>
          ) : (
            <>
            <div style={{ display: "flex", alignItems: "flex-end", gap: 20, marginBottom: 52 }}>
              <SakuraMark size={56} scale={0.145} thickness={1.5} dotBase={4} colorForPetal={(i) => PALETTE[i]} />
              <h1 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 40, margin: 0, letterSpacing: 0.3 }}>
                Your account
              </h1>
            </div>
  
            <div
              className="ks-profile-grid"
              style={{ position: "relative", display: "grid", gridTemplateColumns: "250px minmax(0, 1fr)", gap: 72, alignItems: "start" }}
            >
              <div
                style={{
                  position: "absolute",
                  left: -90,
                  right: -90,
                  top: -80,
                  bottom: -90,
                  background: `radial-gradient(ellipse 62% 58% at 50% 50%, ${PAGE_BG} 0%, ${PAGE_BG} 58%, ${PAGE_BG_FAINT} 78%, transparent 100%)`,
                  pointerEvents: "none",
                }}
              />
  
              <nav style={{ position: "relative", zIndex: 1, padding: "4px 0" }}>
                <span
                  className="ks-profile-nav-line"
                  style={{ position: "absolute", left: 7, top: 14, bottom: 14, width: 1, background: "rgba(0,0,0,0.12)" }}
                />
                <div className="ks-profile-nav" style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  {SECTIONS.map((s) => {
                    const active = section === s.key;
                    return (
                      <button
                        key={s.key}
                        onClick={() => setSection(s.key)}
                        style={{ display: "flex", alignItems: "center", gap: 16, width: "100%", textAlign: "left", padding: "11px 0", border: "none", background: "transparent", cursor: "pointer" }}
                      >
                        <span
                          style={
                            active
                              ? {
                                  width: 15,
                                  height: 15,
                                  flexShrink: 0,
                                  borderRadius: "50%",
                                  background: s.color,
                                  boxShadow: `0 0 0 4px color-mix(in oklch, ${s.color} 18%, transparent), 0 0 0 7px ${PAGE_BG}`,
                                }
                              : { width: 15, height: 15, flexShrink: 0, borderRadius: "50%", background: "#fff", border: "1.5px solid rgba(0,0,0,0.18)", boxSizing: "border-box" }
                          }
                        />
                        <span style={{ fontWeight: active ? 600 : 400, fontSize: 15, color: active ? "oklch(20% 0.01 60)" : "oklch(48% 0.01 60)" }}>
                          {s.label}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </nav>
  
              <div style={{ position: "relative", zIndex: 1, minWidth: 0 }}>
                {section === "profile" && (
                  <form onSubmit={handleProfileSubmit} noValidate>
                    <SectionHeader
                      title="Edit profile"
                      color={PALETTE[0]}
                      body="Your email and username identify you across The Kansei Project. Names are optional and shown only on your public profile."
                    />
  
                    <FieldRow label="Email" error={profileFieldErrors.email}>
                      <PasswordCapableInput
                        type="email"
                        autoComplete="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[0]}
                        error={profileFieldErrors.email}
                      />
                    </FieldRow>
                    <FieldRow label="Username" error={profileFieldErrors.username}>
                      <PasswordCapableInput
                        type="text"
                        autoComplete="username"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[0]}
                        error={profileFieldErrors.username}
                      />
                    </FieldRow>
                    <FieldRow label="First name" error={profileFieldErrors.firstName}>
                      <PasswordCapableInput
                        type="text"
                        autoComplete="given-name"
                        value={firstName}
                        onChange={(e) => setFirstName(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[0]}
                        error={profileFieldErrors.firstName}
                      />
                    </FieldRow>
                    <FieldRow label="Last name" last error={profileFieldErrors.lastName}>
                      <PasswordCapableInput
                        type="text"
                        autoComplete="family-name"
                        value={lastName}
                        onChange={(e) => setLastName(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[0]}
                        error={profileFieldErrors.lastName}
                      />
                    </FieldRow>
  
                    {profileError && (
                      <Banner variant="error" style={{ margin: "16px 0 0" }}>
                        {profileError}
                      </Banner>
                    )}
                    {profileMessage && (
                      <Banner variant="success" style={{ margin: "16px 0 0" }}>
                        {profileMessage}
                      </Banner>
                    )}
  
                    <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 32 }}>
                      <button type="submit" disabled={profileSaving} style={{ ...primaryButtonStyle, width: "auto", padding: "13px 30px" }}>
                        {profileSaving ? "Saving…" : "Save changes"}
                      </button>
                    </div>
                  </form>
                )}
  
                {section === "password" && (
                  <form onSubmit={handlePasswordSubmit} noValidate>
                    <SectionHeader
                      title="Change password"
                      color={PALETTE[2]}
                      body="Use at least 8 characters. You'll stay signed in on this device; other sessions will be signed out."
                    />
  
                    <FieldRow label="Current password" error={passwordFieldErrors.currentPassword}>
                      <PasswordCapableInput
                        type="password"
                        autoComplete="current-password"
                        placeholder="••••••••"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[2]}
                        error={passwordFieldErrors.currentPassword}
                      />
                    </FieldRow>
                    <FieldRow label="New password" error={passwordFieldErrors.newPassword}>
                      <PasswordCapableInput
                        type="password"
                        autoComplete="new-password"
                        placeholder="••••••••"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[2]}
                        error={passwordFieldErrors.newPassword}
                      />
                    </FieldRow>
                    <FieldRow label="Confirm new" last error={passwordFieldErrors.confirmPassword}>
                      <PasswordCapableInput
                        type="password"
                        autoComplete="new-password"
                        placeholder="••••••••"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[2]}
                        error={passwordFieldErrors.confirmPassword}
                      />
                    </FieldRow>
  
                    {passwordError && (
                      <Banner variant="error" style={{ margin: "16px 0 0" }}>
                        {passwordError}
                      </Banner>
                    )}
                    {passwordMessage && (
                      <Banner variant="success" style={{ margin: "16px 0 0" }}>
                        {passwordMessage}
                      </Banner>
                    )}
  
                    <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 32 }}>
                      <button type="submit" disabled={passwordSaving} style={{ ...primaryButtonStyle, width: "auto", padding: "13px 30px" }}>
                        {passwordSaving ? "Saving…" : "Save password"}
                      </button>
                    </div>
                  </form>
                )}
  
                {section === "deactivate" && (
                  <form onSubmit={handleDeactivateSubmit} noValidate>
                    <SectionHeader title="Deactivate account" color={PALETTE[1]} />
  
                    <Banner variant="warning" size="large" style={{ marginBottom: 30, maxWidth: 620 }}>
                      Deactivating hides your profile and cancels active services. Your data is kept for 30 days,
                      after which it is permanently deleted. This cannot be undone once the 30 days pass.
                    </Banner>
  
                    <FieldRow label="Current password" last error={deactivateFieldErrors.currentPassword}>
                      <PasswordCapableInput
                        type="password"
                        autoComplete="current-password"
                        placeholder="••••••••"
                        value={deactivatePassword}
                        onChange={(e) => setDeactivatePassword(e.target.value)}
                        baseStyle={inputStyleWhite}
                        focusColor={PALETTE[1]}
                        error={deactivateFieldErrors.currentPassword}
                      />
                    </FieldRow>
  
                    {deactivateError && (
                      <Banner variant="error" style={{ margin: "16px 0 0" }}>
                        {deactivateError}
                      </Banner>
                    )}
  
                    <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 32 }}>
                      <button type="submit" disabled={deactivating} className="ks-deactivate-btn">
                        {deactivating ? "Deactivating…" : "Deactivate my account"}
                      </button>
                    </div>
                  </form>
                )}
              </div>
            </div>
            </>
          )}
        </div>
      </main>
    </div>
  );
}

function SectionHeader({ title, color, body }: { title: string; color: string; body?: string }) {
  return (
    <div style={{ marginBottom: 34 }}>
      <h2 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 27, margin: "0 0 12px" }}>{title}</h2>
      <span style={{ display: "block", width: 56, height: 2, borderRadius: 1, background: color }} />
      {body && <p style={{ fontSize: 14, lineHeight: 1.7, color: TEXT_SECONDARY, margin: "16px 0 0", maxWidth: 520 }}>{body}</p>}
    </div>
  );
}
