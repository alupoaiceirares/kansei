import SakuraMark from "./SakuraMark";
import { PALETTE } from "@/lib/design/palette";
import type { Style } from "@/lib/design/styleTypes";

type Props = {
  size?: number;
  style?: Style;
};

/** Spinning sakura mark, full palette - the site's own loading indicator instead of a generic spinner. */
export default function SakuraSpinner({ size = 48, style }: Props) {
  return (
    <SakuraMark
      size={size}
      scale={size / 390} // matches the crest/success-mark ratio used elsewhere (e.g. size 56 -> scale 0.145)
      thickness={1.5}
      dotBase={4}
      colorForPetal={(i) => PALETTE[i]}
      spin
      spinDurationS={1.6} // the hero flower's 100s reads as static at this size - a loading indicator needs to actually read as spinning
      style={style}
    />
  );
}
