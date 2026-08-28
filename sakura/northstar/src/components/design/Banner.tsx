import type { ReactNode } from "react";
import type { Style } from "@/lib/design/styleTypes";
import { PALETTE } from "@/lib/design/palette";

type Variant = "error" | "success" | "warning";
type Size = "default" | "large";

const VARIANT_COLOR: Record<Variant, string> = {
  error: PALETTE[1], // dark red
  warning: PALETTE[1], // same red, used for the deactivate-account panel
  success: PALETTE[4], // dark green
};

const VARIANT_TEXT: Record<Variant, string> = {
  error: "oklch(40% 0.13 25)",
  warning: "oklch(40% 0.13 25)",
  success: "oklch(35% 0.1 150)",
};

const SIZE_STYLE: Record<Size, { padding: string; barWidth: number; radius: number }> = {
  default: { padding: "12px 16px", barWidth: 6, radius: 12 },
  large: { padding: "20px 22px", barWidth: 8, radius: 14 },
};

type Props = {
  variant: Variant;
  size?: Size;
  children: ReactNode;
  style?: Style;
};

/**
 * Colored panel (soft tinted background, matching border, left accent bar), same visual language as the profile page's deactivate-account warning, reused for form validation errors and success confirmations so they're as visible as that panel
 */
export default function Banner({ variant, size = "default", children, style }: Props) {
  const color = VARIANT_COLOR[variant];
  const { padding, barWidth, radius } = SIZE_STYLE[size];

  return (
    <div
      style={{
        display: "flex",
        gap: size === "large" ? 16 : 12,
        padding,
        borderRadius: radius,
        background: `color-mix(in oklch, ${color} 7%, white)`,
        border: `1px solid color-mix(in oklch, ${color} 20%, white)`,
        ...style,
      }}
    >
      <span
        style={{
          width: barWidth,
          flexShrink: 0,
          borderRadius: barWidth / 2,
          background: color,
          opacity: 0.65,
        }}
      />
      <p style={{ fontSize: 13, lineHeight: 1.6, color: VARIANT_TEXT[variant], margin: 0 }}>{children}</p>
    </div>
  );
}
