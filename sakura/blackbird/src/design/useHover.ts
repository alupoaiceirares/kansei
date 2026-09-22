import { useState, type CSSProperties } from 'react';

/**
 * The design expresses hover as a second inline style, so elements merge a hover patch over
 * their base style instead of carrying a class.
 */
export function useHoverStyle(base: CSSProperties, hover: CSSProperties) {
  const [hovered, setHovered] = useState(false);
  return {
    style: hovered ? { ...base, ...hover } : base,
    onMouseEnter: () => setHovered(true),
    onMouseLeave: () => setHovered(false),
    onFocus: () => setHovered(true),
    onBlur: () => setHovered(false),
  };
}
