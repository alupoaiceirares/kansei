import type { InputHTMLAttributes, ReactNode } from "react";
import type { Style } from "@/lib/design/styleTypes";
import { fieldLabelStyle, inputStyle as defaultInputStyle } from "@/lib/design/formStyles";
import PasswordCapableInput, { FIELD_ERROR_COLOR } from "./PasswordCapableInput";

type Props = {
  label: ReactNode;
  error?: string;
  baseStyle?: Style;
  focusColor?: string;
  wrapperStyle?: Style;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "style">;

/**
 * Label above input, matching every auth-page field in the design bundle. Red outline + inline message when `error` is set, show/hide toggle for type="password", both via PasswordCapableInput
 */
export default function Field({ label, error, baseStyle, focusColor, wrapperStyle, ...rest }: Props) {
  return (
    <label style={{ display: "flex", flexDirection: "column", gap: 7, ...wrapperStyle }}>
      <span style={fieldLabelStyle}>{label}</span>
      <PasswordCapableInput baseStyle={baseStyle ?? defaultInputStyle} focusColor={focusColor} error={error} {...rest} />
      {error && <span style={{ fontSize: 12, color: FIELD_ERROR_COLOR }}>{error}</span>}
    </label>
  );
}
