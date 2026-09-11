"use client";

import { Suspense, useSyncExternalStore } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import SakuraMark from "@/components/design/SakuraMark";
import WirehoodMark from "@/components/design/WirehoodMark";
import { getToken, subscribeToken } from "@/lib/auth";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import {
  FOOTER_BG,
  HAIRLINE,
  MODAL_SHADOW,
  PAGE_BG,
  PALETTE,
  TEXT_MUTED,
  TEXT_SECONDARY,
} from "@/lib/design/palette";
import { primaryButtonStyle } from "@/lib/design/formStyles";
import { DISPLAY_FONT } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 2780;
const FLOWER_CY = 800;
const FLOWER_SAFE_R = 390;
const TIMELINE_TOP = 1400;
const TIMELINE_BOTTOM = 2560;
const TIMELINE_HALF_W = 360;

const SOUNDWAVE_URL = process.env.NEXT_PUBLIC_SOUNDWAVE_URL;

type TimelineEntry = { title: string; text: string; isApp?: boolean };

const TIMELINE_COPY: TimelineEntry[] = [
  {
    title: "Wirehood",
    text: "Search, download and play music and video from one archive the whole community builds — plus shared playlists, friends and listening stats.",
    isApp: true,
  },
  { title: "Service 2", text: "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Placeholder description." },
  { title: "Service 3", text: "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris. Placeholder description." },
  { title: "Service 4", text: "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum. Placeholder description." },
];

const socialStyle = {
  width: 36,
  height: 36,
  borderRadius: "50%",
  background: "#fff",
  border: "1px solid rgba(0,0,0,0.1)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  fontWeight: 500,
  fontSize: 12,
  color: "oklch(45% 0.01 60)",
} as const;

function LandingInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { vw, isMobile } = useViewportWidth();
  const confirmed = searchParams.get("confirmed") === "1";
  const token = useSyncExternalStore(
    subscribeToken,
    () => getToken(),
    () => null,
  );
  // Not logged in yet -> send through login first; the real handoff (#token=) only makes sense with a session
  const wirehoodHref = token ? `${SOUNDWAVE_URL}/landing#token=${encodeURIComponent(token)}` : "/login";

  function closeConfirm() {
    router.replace("/");
  }

  const cx = vw / 2;
  const skip = isMobile
    ? () => false
    : (hx: number, hy: number) => {
        if (Math.hypot(hx - cx, hy - FLOWER_CY) < FLOWER_SAFE_R - 40) return true;
        if (hy > TIMELINE_TOP && hy < TIMELINE_BOTTOM && Math.abs(hx - cx) < TIMELINE_HALF_W - 60) return true;
        return false;
      };

  // Desktop flower is absolutely positioned at a fixed y (matching the design's fixed clearing math above); mobile drops that in favor of normal flow, since a fixed y would drift once hero text wraps to a different height
  const heroFlowerScale = isMobile ? 1.1 : 2.2;
  const flowerRatio = heroFlowerScale / 2.2;

  return (
    <div style={{ position: "relative", width: "100%", overflowX: "hidden" }}>
      <HexLatticeBackground vw={vw} height={LATTICE_HEIGHT} skip={skip} />

      {!isMobile && (
        <>
          <div
            style={{
              position: "absolute",
              top: FLOWER_CY,
              left: "50%",
              width: 1120,
              height: 1120,
              margin: "-560px 0 0 -560px",
              zIndex: 0,
              pointerEvents: "none",
              background: `radial-gradient(circle closest-side, ${PAGE_BG} 0%, ${PAGE_BG} 76%, transparent 100%)`,
            }}
          />
          <div
            style={{
              position: "absolute",
              top: TIMELINE_TOP,
              left: "50%",
              width: 1000,
              height: 1160,
              marginLeft: -500,
              zIndex: 0,
              pointerEvents: "none",
              background: `linear-gradient(to right, transparent 0%, ${PAGE_BG} 14%, ${PAGE_BG} 86%, transparent 100%)`,
            }}
          />
        </>
      )}

      <Header overlay />

      <section
        style={{
          position: "relative",
          minHeight: isMobile ? undefined : "max(100vh, 1400px)",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "flex-start",
          padding: isMobile ? "140px 24px 64px" : "150px 24px 0",
          boxSizing: "border-box",
        }}
      >
        <SakuraMark
          size={1000 * flowerRatio}
          scale={heroFlowerScale}
          colorForPetal={(i) => `color-mix(in oklch, ${PALETTE[i]}, white 22%)`}
          spin
          stamens={{ color: (i) => `color-mix(in oklch, ${PALETTE[i]}, white 22%)` }}
          centerDot={{
            size: 18 * flowerRatio,
            background: PAGE_BG,
            border: `2px solid color-mix(in oklch, ${PALETTE[2]}, white 22%)`,
          }}
          style={
            isMobile
              ? { position: "relative", zIndex: 0, margin: "0 0 24px" }
              : {
                  position: "absolute",
                  top: FLOWER_CY,
                  left: "50%",
                  margin: `${-500}px 0 0 -500px`,
                  zIndex: 0,
                }
          }
        />

        <div style={{ position: "relative", zIndex: 2, textAlign: "center", maxWidth: 380 }}>
          <h1
            style={{
              fontFamily: DISPLAY_FONT,
              fontWeight: 500,
              fontSize: "clamp(30px, 4.2vw, 52px)",
              margin: 0,
              letterSpacing: 0.5,
            }}
          >
            The Kansei Project
          </h1>
          <p style={{ fontSize: 16, lineHeight: 1.6, color: "oklch(38% 0.01 60)", margin: "20px 0 0" }}>
            Kansei (感性) — a Japanese concept describing the emotional and sensory response evoked
            by a product or experience.
          </p>
          <p style={{ fontStyle: "italic", fontSize: 13, lineHeight: 1.6, color: "oklch(55% 0.01 60)", margin: "14px 0 0" }}>
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Placeholder copy — replace
            with the project&apos;s own description.
          </p>
        </div>

        {!isMobile && (
          <div
            style={{
              position: "absolute",
              bottom: 48,
              left: 0,
              right: 0,
              display: "flex",
              justifyContent: "center",
              animation: "kansei-bob 2.4s ease-in-out infinite",
              zIndex: 3,
              pointerEvents: "none",
            }}
          >
            <span
              style={{
                width: 34,
                height: 34,
                borderRight: "5px solid oklch(58% 0.19 345)",
                borderBottom: "5px solid oklch(58% 0.19 345)",
                borderRadius: "0 0 5px 0",
                transform: "rotate(45deg)",
              }}
            />
          </div>
        )}
      </section>

      <section style={{ position: "relative", padding: "100px 24px 140px", maxWidth: 680, margin: "0 auto" }}>
        <h2 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 34, textAlign: "center", margin: "0 0 80px" }}>
          Timeline
        </h2>
        <div
          style={{
            position: "absolute",
            top: 150,
            bottom: 80,
            left: "50%",
            width: 2,
            background: "rgba(0,0,0,0.1)",
            transform: "translateX(-1px)",
          }}
        />
        {TIMELINE_COPY.map(({ title, text, isApp }, i) => {
          const isLeft = i % 2 === 0;
          const color = PALETTE[i % PALETTE.length];
          const tintStyle = {
            background: `color-mix(in oklch, ${color} 13%, white)`,
            border: `1px solid color-mix(in oklch, ${color} 28%, white)`,
            borderRadius: 16,
            padding: "26px 30px",
            display: "inline-block",
          } as const;
          const cardBody = isApp ? (
            <a href={wirehoodHref} style={{ display: "block", textAlign: "left" }}>
              <div style={tintStyle}>
                <div className="ks-timeline-app-panel">
                  <WirehoodMark scale={0.6} />
                </div>
                <p style={{ fontSize: 14, lineHeight: 1.6, color: "oklch(45% 0.01 60)", margin: "0 0 12px" }}>{text}</p>
                <span style={{ display: "inline-flex", alignItems: "center", gap: 7, fontWeight: 600, fontSize: 13.5, color: "oklch(42% 0.14 152)" }}>
                  Open {title}
                  <svg width={14} height={14} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round">
                    <path d="M5 12h14M14 7l5 5-5 5" />
                  </svg>
                </span>
              </div>
            </a>
          ) : (
            <div style={tintStyle}>
              <h3 style={{ fontWeight: 600, fontSize: 18, margin: "0 0 8px", color: "oklch(22% 0.01 60)" }}>{title}</h3>
              <p style={{ fontSize: 14, lineHeight: 1.6, color: "oklch(45% 0.01 60)", margin: 0 }}>{text}</p>
            </div>
          );
          return (
            <div
              key={title}
              className="ks-timeline-entry"
              style={{ display: "grid", gridTemplateColumns: "1fr 48px 1fr", alignItems: "start", marginBottom: 56 }}
            >
              <div
                className="ks-timeline-col-left"
                style={{ textAlign: "right", paddingRight: 32, display: "flex", justifyContent: "flex-end" }}
              >
                {isLeft && cardBody}
              </div>
              <div style={{ display: "flex", justifyContent: "center", paddingTop: 4 }}>
                <div
                  style={{
                    width: 14,
                    height: 14,
                    borderRadius: "50%",
                    background: color,
                    border: `3px solid ${PAGE_BG}`,
                    boxShadow: "0 0 0 1px rgba(0,0,0,0.1)",
                  }}
                />
              </div>
              <div
                className="ks-timeline-col-right"
                style={{ textAlign: "left", paddingLeft: 32, display: "flex", justifyContent: "flex-start" }}
              >
                {!isLeft && cardBody}
              </div>
              <div className="ks-timeline-col-mobile">{cardBody}</div>
            </div>
          );
        })}
      </section>

      <footer style={{ position: "relative", borderTop: `1px solid ${HAIRLINE}`, background: FOOTER_BG, padding: "56px 40px 32px" }}>
        <div style={{ maxWidth: 1000, margin: "0 auto", display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 40 }}>
          <div style={{ maxWidth: 260 }}>
            <div style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 20, marginBottom: 10 }}>
              The Kansei Project
            </div>
            <p style={{ fontSize: 13, lineHeight: 1.6, color: TEXT_SECONDARY, margin: 0 }}>
              Placeholder tagline about the project and what it brings together.
            </p>
          </div>
          <div style={{ display: "flex", gap: 56, flexWrap: "wrap" }}>
            <FooterCol
              heading="Project"
              links={[
                ["About", "#"],
                ["Services", "#"],
                ["Timeline", "#"],
              ]}
            />
            <FooterCol
              heading="Account"
              links={[
                ["Log in", "/login"],
                ["Register", "/register"],
              ]}
            />
            <FooterCol
              heading="Contact"
              links={[
                ["Support", "#"],
                ["Contact us", "#"],
              ]}
            />
          </div>
          <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
            {["X", "in", "gh"].map((label) => (
              <a href="#" style={socialStyle} key={label}>
                {label}
              </a>
            ))}
          </div>
        </div>
        <div style={{ maxWidth: 1000, margin: "40px auto 0", paddingTop: 20, borderTop: "1px solid rgba(0,0,0,0.06)", textAlign: "center" }}>
          <span style={{ fontSize: 12, color: "oklch(55% 0.01 60)" }}>© 2026 The Kansei Project. All rights reserved.</span>
        </div>
      </footer>

      {confirmed && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 60,
            background: "oklch(30% 0.02 60 / 0.38)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 24,
          }}
        >
          <div style={{ width: "100%", maxWidth: 430, background: "#fff", borderRadius: 22, boxShadow: MODAL_SHADOW, padding: "40px 38px 32px", textAlign: "center" }}>
            <SakuraMark
              size={74}
              scale={0.19}
              thickness={1.5}
              dotBase={4.5}
              colorForPetal={(i) => PALETTE[i]}
              style={{ margin: "0 auto 24px" }}
            />
            <div
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                padding: "6px 15px",
                borderRadius: 999,
                background: "color-mix(in oklch, oklch(42% 0.12 150) 10%, white)",
                border: "1px solid color-mix(in oklch, oklch(42% 0.12 150) 24%, white)",
                marginBottom: 18,
              }}
            >
              <span style={{ width: 7, height: 7, borderRadius: "50%", background: PALETTE[4] }} />
              <span style={{ fontWeight: 600, fontSize: 11, letterSpacing: 1.6, textTransform: "uppercase", color: "oklch(38% 0.11 150)" }}>
                Email confirmed
              </span>
            </div>
            <h2 style={{ fontFamily: DISPLAY_FONT, fontWeight: 500, fontSize: 27, margin: "0 0 12px" }}>You&apos;re all set</h2>
            <p style={{ fontSize: 14, lineHeight: 1.7, color: TEXT_SECONDARY, margin: "0 0 28px" }}>
              Your email is confirmed and you&apos;re signed in. Explore the services or finish
              setting up your profile.
            </p>
            <button onClick={closeConfirm} style={primaryButtonStyle}>
              Start exploring
            </button>
            <Link
              href="/profile"
              style={{ display: "block", marginTop: 12, padding: "11px 0", fontWeight: 500, fontSize: 13, color: TEXT_SECONDARY }}
            >
              Go to profile settings
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function FooterCol({ heading, links }: { heading: string; links: [string, string][] }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      <span style={{ fontWeight: 600, fontSize: 12, letterSpacing: 0.6, textTransform: "uppercase", color: TEXT_MUTED, marginBottom: 4 }}>
        {heading}
      </span>
      {links.map(([label, href]) => (
        <Link href={href} style={{ fontSize: 14 }} key={label}>
          {label}
        </Link>
      ))}
    </div>
  );
}

export default function LandingPage() {
  return (
    <Suspense fallback={null}>
      <LandingInner />
    </Suspense>
  );
}
