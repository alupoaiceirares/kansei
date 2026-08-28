"use client";

import { useEffect, useState } from "react";

const MOBILE_BREAKPOINT = 700;
const TABLET_BREAKPOINT = 900;

/**
 * Live viewport width, used both to recompute the hex lattice and to drive the layout collapses the design bundle left unspecified (timeline, profile grid, hero)
 */
export function useViewportWidth() {
  const [vw, setVw] = useState(1200);

  useEffect(() => {
    const onResize = () => setVw(window.innerWidth);
    onResize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  return { vw, isMobile: vw < MOBILE_BREAKPOINT, isTablet: vw < TABLET_BREAKPOINT };
}
