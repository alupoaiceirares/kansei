"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Header from "@/components/design/Header";
import HexLatticeBackground from "@/components/design/HexLatticeBackground";
import { useViewportWidth } from "@/lib/design/useViewportWidth";
import { apiFetch } from "@/lib/api";
import { setToken } from "@/lib/auth";
import { TEXT_SECONDARY } from "@/lib/design/palette";
import { authCardH1 } from "@/lib/design/textStyles";

const LATTICE_HEIGHT = 900;

function VerifyEmailInner() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { vw } = useViewportWidth();
  const token = searchParams.get("token");

  const [failed, setFailed] = useState(!token);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;

    apiFetch("/api/auth/verify-email", {
      method: "POST",
      body: JSON.stringify({ token }),
    })
      .then(async (res) => {
        if (cancelled) return;
        const body = await res.json().catch(() => null);

        if (!res.ok) {
          setFailed(true);
          return;
        }

        setToken(body.token);
        router.push("/?confirmed=1");
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
    };
  }, [token, router]);

  useEffect(() => {
    if (failed) router.push("/link-expired");
  }, [failed, router]);

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
      <Header showHamburger={false} rightVariant="none" />
      <main
        style={{
          position: "relative",
          zIndex: 2,
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "72px 24px 96px",
          textAlign: "center",
        }}
      >
        <div>
          <h1 style={authCardH1}>Confirming your email</h1>
          <p style={{ fontSize: 14, color: TEXT_SECONDARY }}>One moment…</p>
        </div>
      </main>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailInner />
    </Suspense>
  );
}
