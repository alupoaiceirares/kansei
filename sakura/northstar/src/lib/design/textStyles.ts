import type { Style } from "./styleTypes";

// next/font exposes the loaded face only through its CSS variable - a literal 'Shippori Mincho' string here would silently fall back to an unstyled system font
export const DISPLAY_FONT = "var(--font-shippori-mincho), serif";

// Body/UI text inherits Inter from `body`/`input,button` in globals.css - this constant only exists for the rare inline override that needs to be explicit about it
export const UI_FONT = "var(--font-inter), system-ui, sans-serif";

export const authCardH1: Style = {
  fontFamily: DISPLAY_FONT,
  fontWeight: 500,
  fontSize: 30,
  margin: "0 0 8px",
  letterSpacing: 0.3,
};

export const authCardSubline: Style = {
  fontWeight: 400,
  fontSize: 14,
  lineHeight: 1.6,
  margin: "0 0 30px",
};

export const modalH2: Style = {
  fontFamily: DISPLAY_FONT,
  fontWeight: 500,
  fontSize: 23,
  margin: "0 0 8px",
};
