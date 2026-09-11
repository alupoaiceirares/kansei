"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
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

export default function Header({
  overlay = false,
  rightVariant = "auto",
  showHamburger = true,
  highlightProfile = false,
}: Props) {
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuContainerRef = useRef<HTMLDivElement>(null);
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

  // Close the dropdown on any click outside it - it previously only closed via the
  // hamburger button itself, which reads as stuck/unresponsive when clicking elsewhere.
  useEffect(() => {
    if (!menuOpen) return;
    function handleOutsideClick(e: MouseEvent) {
      if (menuContainerRef.current && !menuContainerRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, [menuOpen]);

  async function handleSignOut() {
    await logout();
    router.push("/");
  }

  // Not logged in yet -> send through login first; the real handoff (#token=) only makes sense with a session
  const wirehoodHref = loggedIn
    ? `${process.env.NEXT_PUBLIC_SOUNDWAVE_URL}/landing#token=${encodeURIComponent(getToken() ?? "")}`
    : "/login";

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
        <Link href="/login" className="ks-pill ks-pill--fill">
          Log in
        </Link>
      )}
      {showRegister && (
        <Link href="/register" className="ks-pill ks-pill--outline">
          Register
        </Link>
      )}
      {showSignOut && (
        <button onClick={handleSignOut} className="ks-pill ks-pill--signout">
          Sign out
        </button>
      )}

      {showHamburger && (
        <div ref={menuContainerRef} style={{ position: "relative", flexShrink: 0 }}>
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
                minWidth: 280,
                padding: 16,
                borderRadius: 16,
                background: "#fff",
                border: "1px solid rgba(0,0,0,0.08)",
                boxShadow: "0 12px 32px rgba(0,0,0,0.12)",
                display: "flex",
                flexDirection: "column",
                gap: 6,
              }}
            >
              <div
                style={{
                  padding: "10px 20px 10px",
                  font: "600 13px 'Inter', sans-serif",
                  letterSpacing: 1.4,
                  textTransform: "uppercase",
                  color: TEXT_MUTED,
                }}
              >
                Services
              </div>
              <a href={wirehoodHref} className="ks-svc-ring ks-svc-ring--1">
                <span
                  className="ks-svc-ring__inner"
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 10 }}
                >
                  <span>Wirehood</span>
                  <span style={{ fontWeight: 500, fontSize: 11, letterSpacing: "0.08em", textTransform: "uppercase", color: "oklch(48% 0.18 25)" }}>
                    Music
                  </span>
                </span>
              </a>
              <a href="#" className="ks-svc-ring ks-svc-ring--2">
                <span className="ks-svc-ring__inner">Service 2</span>
              </a>
              <a href="#" className="ks-svc-ring ks-svc-ring--3">
                <span className="ks-svc-ring__inner">Service 3</span>
              </a>
              <span style={{ display: "block", height: 1, margin: "12px 10px", background: "rgba(0,0,0,0.07)" }} />
              {loggedIn && (
                <Link href="/profile" className={`ks-menu-row${highlightProfile ? " ks-menu-row--active" : ""}`}>
                  Profile
                </Link>
              )}
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
