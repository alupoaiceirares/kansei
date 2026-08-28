import type { CSSProperties } from "react";

// CSS custom properties (--full, --dim) and offset-path/offset-distance aren't part of the standard CSSProperties type - this bundle's animations rely on both
export type Style = CSSProperties & Record<string, string | number | undefined>;
