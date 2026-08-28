import type { Style } from "./styleTypes";
import { INPUT_BORDER, PRIMARY_BUTTON_GRADIENT, TEXT_BODY } from "./palette";

// Focus border color is applied via the .ks-input:focus rule in design.css (inline styles can't express :focus), this is only the resting state 
// Family inherits from the `input` rule in globals.css (var(--font-inter)), no shorthand `font` here since CSS custom properties inside a shorthand's family slot aren't reliably supported
export const inputStyle: Style = {
  width: "100%",
  boxSizing: "border-box",
  padding: "12px 14px",
  borderRadius: 10,
  border: `1.5px solid ${INPUT_BORDER}`,
  background: "oklch(98.5% 0.004 70)",
  fontWeight: 400,
  fontSize: 15,
  color: TEXT_BODY,
};

// Profile page rows use a plain-white input instead (design's own variant)
export const inputStyleWhite: Style = { ...inputStyle, background: "#fff", maxWidth: 400 };

export const primaryButtonStyle: Style = {
  width: "100%",
  padding: "14px 20px",
  border: "none",
  borderRadius: 999,
  cursor: "pointer",
  fontWeight: 600,
  fontSize: 15,
  color: "#fff",
  background: PRIMARY_BUTTON_GRADIENT,
};

export const secondaryLinkStyle: Style = {
  display: "block",
  width: "100%",
  boxSizing: "border-box",
  textAlign: "center",
  padding: "13px 20px",
  borderRadius: 999,
  border: "1.5px solid rgba(0,0,0,0.12)",
  fontWeight: 500,
  fontSize: 14,
  color: "oklch(35% 0.01 60)",
};

export const fieldLabelStyle: Style = {
  fontWeight: 500,
  fontSize: 12,
  letterSpacing: 0.4,
  textTransform: "uppercase",
  color: "oklch(45% 0.01 60)",
};
