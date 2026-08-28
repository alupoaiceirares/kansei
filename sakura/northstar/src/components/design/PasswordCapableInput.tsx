"use client";

import { useState, type InputHTMLAttributes } from "react";
import type { Style } from "@/lib/design/styleTypes";
import { PALETTE } from "@/lib/design/palette";

export const FIELD_ERROR_COLOR = PALETTE[1]; // dark red - same accent Banner uses for "error"

type Props = {
  error?: string;
  baseStyle: Style;
  focusColor?: string;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "style">;

/**
 * The `<input>` itself: red outline when `error` is set, plus a show/hide toggle for type="password". 
 * Shared by the stacked label-above-input layout (auth pages' Field) and the label-left grid rows on the profile page, same input behavior, two different surrounding layouts
 */
export default function PasswordCapableInput({ error, baseStyle, focusColor, type, ...rest }: Props) {
  const [reveal, setReveal] = useState(false);
  const isPassword = type === "password";
  const resolvedType = isPassword ? (reveal ? "text" : "password") : type;

  const style: Style = {
    ...baseStyle,
    ...(focusColor ? { "--focus-color": focusColor } : {}),
    ...(isPassword ? { paddingRight: 42 } : {}),
    ...(error ? { borderColor: FIELD_ERROR_COLOR } : {}),
  };

  return (
    <span style={{ position: "relative", display: "block" }}>
      <input type={resolvedType} className="ks-input" style={style} {...rest} />
      {isPassword && (
        <button
          type="button"
          onClick={() => setReveal((v) => !v)}
          aria-label={reveal ? "Hide password" : "Show password"}
          style={{
            position: "absolute",
            right: 4,
            top: "50%",
            transform: "translateY(-50%)",
            width: 32,
            height: 32,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            border: "none",
            background: "transparent",
            cursor: "pointer",
            color: "oklch(55% 0.01 60)",
            padding: 0,
          }}
        >
          <EyeIcon open={reveal} />
        </button>
      )}
    </span>
  );
}

export function EyeIcon({ open }: { open: boolean }) {
  if (open) {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a20.3 20.3 0 0 1 5.06-6.06M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a20.5 20.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
        <path d="M1 1l22 22" />
      </svg>
    );
  }
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}
