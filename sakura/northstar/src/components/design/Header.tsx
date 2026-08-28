"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import SakuraMark from "./SakuraMark";
import { getToken, logout, subscribeToken } from "@/lib/auth";
import { apiFetch } from "@/lib/api";
import { HEADER_GRADIENT, TEXT_MUTED } from "@/lib/design/palette";

type RightVariant = "auto" | "loginOnly" | "registerOnly" | "both" | "none";

type Props = {
  /** Landing page overlays the header on the hero (position: absolute); every other page keeps it in flow */
  overlay?: boolean;
  rightVariant?: RightVariant;
  showHamburger?: boolean;
  /** Profile page highlights its own row in the dropdown */
  highlightProfile?: boolean;
};

const pillBase = {
  fontWeight: 500,
  fontSize: 14,
  padding: "9px 20px",
  borderRadius: 999,
  whiteSpace: "nowrap" as const,
  flexShrink: 0,
};

export default function Header({
  overlay = false,
  rightVariant = "auto",
  showHamburger = true,
  highlightProfile = false,
}: Props) {
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const loggedIn = useSyncExternalStore(
    subscribeToken,
    () => !!getToken(),
    () => false,
  );

  // A token can go stale without the browser ever finding out (password/email change on another device or tab, or just expiring), localStorage still has it, so getToken() alone would keep showing "logged in" until some other request happens to 401
  // Verify it once per page view so the header (and everything reading subscribeToken) reflects the real session instead of a stale local flag
  useEffect(() => {
    if (!getToken()) return;
    apiFetch("/api/auth/me").catch(() => {});
  }, []);

  async function handleSignOut() {
    await logout();
    router.push("/");
  }

  const showLogin = rightVariant === "both" || rightVariant === "loginOnly" || (rightVariant === "auto" && !loggedIn);
  const showRegister = rightVariant === "both" || rightVariant === "registerOnly" || (rightVariant === "auto" && !loggedIn);
  const showSignOut = rightVariant === "auto" && loggedIn;

  return (
    <header
      style={{
        position: overlay ? "absolute" : "relative",
        top: overlay ? 0 : undefined,
        left: overlay ? 0 : undefined,
        right: overlay ? 0 : undefined,
        zIndex: 10,
        display: "flex",
        justifyContent: "flex-end",
        alignItems: "center",
        gap: 12,
        padding: "24px 40px",
        background: HEADER_GRADIENT,
      }}
    >
      <Link
        href="/"
        style={{ position: "absolute", left: 34, top: "50%", width: 76, height: 76, marginTop: -38 }}
      >
        <SakuraMark
          size={76}
          scale={0.2}
          thickness={1.5}
          dotBase={3.5}
          opacity={0.75}
          colorForPetal={() => "rgba(255,255,255,0.75)"}
        />
      </Link>

      {showLogin && (
        <Link href="/login" style={{ ...pillBase, background: "rgba(255,255,255,0.16)", color: "#fff" }}>
          Log in
        </Link>
      )}
      {showRegister && (
        <Link
          href="/register"
          style={{ ...pillBase, border: "1.5px solid rgba(255,255,255,0.85)", color: "#fff" }}
        >
          Register
        </Link>
      )}
      {showSignOut && (
        <button
          onClick={handleSignOut}
          style={{
            ...pillBase,
            border: "1px solid rgba(255,255,255,0.5)",
            background: "transparent",
            color: "rgba(255,255,255,0.92)",
            cursor: "pointer",
          }}
        >
          Sign out
        </button>
      )}

      {showHamburger && (
        <div style={{ position: "relative", flexShrink: 0 }}>
          <button
            onClick={() => setMenuOpen((v) => !v)}
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 5,
              padding: "8px 4px",
              border: "none",
              background: "transparent",
              cursor: "pointer",
            }}
          >
            <span style={{ display: "block", width: 24, height: 2, borderRadius: 1, background: "#fff" }} />
            <span style={{ display: "block", width: 24, height: 2, borderRadius: 1, background: "#fff" }} />
            <span style={{ display: "block", width: 24, height: 2, borderRadius: 1, background: "#fff" }} />
          </button>

          {menuOpen && (
            <div
              style={{
                position: "absolute",
                right: 0,
                top: "calc(100% + 10px)",
                minWidth: 190,
                padding: 8,
                borderRadius: 12,
                background: "#fff",
                border: "1px solid rgba(0,0,0,0.08)",
                boxShadow: "0 12px 32px rgba(0,0,0,0.12)",
                display: "flex",
                flexDirection: "column",
                gap: 2,
              }}
            >
              <div
                style={{
                  padding: "6px 14px 6px",
                  font: "600 11px 'Inter', sans-serif",
                  letterSpacing: 1.4,
                  textTransform: "uppercase",
                  color: TEXT_MUTED,
                }}
              >
                Services
              </div>
              <a href="#" className="ks-svc-ring ks-svc-ring--1">
                <span className="ks-svc-ring__inner">Service 1</span>
              </a>
              <a href="#" className="ks-svc-ring ks-svc-ring--2">
                <span className="ks-svc-ring__inner">Service 2</span>
              </a>
              <a href="#" className="ks-svc-ring ks-svc-ring--3">
                <span className="ks-svc-ring__inner">Service 3</span>
              </a>
              <span style={{ display: "block", height: 1, margin: "8px 6px", background: "rgba(0,0,0,0.07)" }} />
              <Link href="/profile" className={`ks-menu-row${highlightProfile ? " ks-menu-row--active" : ""}`}>
                Profile
              </Link>
              <a href="#" className="ks-menu-row">
                Timeline
              </a>
              <a href="#" className="ks-menu-row">
                About
              </a>
              <a href="#" className="ks-menu-row">
                Support
              </a>
            </div>
          )}
        </div>
      )}
    </header>
  );
}
