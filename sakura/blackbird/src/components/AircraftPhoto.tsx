import { useEffect, useState } from 'react';
import { COLORS } from '../design/tokens';
import { fetchImageUrl } from '../api/client';
import { fetchPhotoInfoForFamily, fetchPhotoInfoForType } from '../api/tailwind';
import type { PhotoInfo } from '../api/types';

type Props = {
  aircraftTypeId?: number | null;
  family?: string | null;
  width: number;
  height: number;
};

/**
 * One photo per aircraft type, from Wikimedia Commons. A type with no approved photo falls back
 * to the silhouette, and wherever a photo shows large its author and licence show with it.
 */
export function AircraftPhoto({ aircraftTypeId, family, width, height }: Props) {
  const [url, setUrl] = useState<string | null>(null);
  const [info, setInfo] = useState<PhotoInfo | null>(null);

  useEffect(() => {
    let live = true;
    let objectUrl: string | null = null;

    const path = aircraftTypeId
      ? { path: '/tailwind/aircraft-types/' + aircraftTypeId + '/photo', query: undefined }
      : family
        ? { path: '/tailwind/aircraft-families/photo', query: { name: family } }
        : null;

    if (path) {
      fetchImageUrl(path.path, path.query)
        .then((loaded) => {
          if (!live) {
            if (loaded) URL.revokeObjectURL(loaded);
            return;
          }
          objectUrl = loaded;
          setUrl(loaded);
        })
        .catch(() => undefined);

      const infoCall = aircraftTypeId ? fetchPhotoInfoForType(aircraftTypeId) : fetchPhotoInfoForFamily(family as string);
      infoCall.then((loaded) => live && setInfo(loaded)).catch(() => undefined);
    }

    return () => {
      live = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [aircraftTypeId, family]);

  const hasPhoto = !!url && !!info?.hasPhoto;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flexShrink: 0 }}>
      <div
        style={{
          position: 'relative',
          width,
          height,
          borderRadius: 11,
          background: '#082834',
          border: '1px solid ' + COLORS.line,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {hasPhoto ? (
          <img src={url as string} alt={info?.title ?? 'Aircraft'} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        ) : (
          <>
            <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} aria-hidden="true">
              <g fill="none" stroke="#123E4D" strokeWidth={1}>
                <path d={`M-10 ${height * 0.76} C${width * 0.2} ${height * 0.42} ${width * 0.73} ${height * 0.37} ${width + 10} ${height * 0.59}`} />
                <path d={`M-10 ${height * 0.91} C${width * 0.24} ${height * 0.63} ${width * 0.77} ${height * 0.57} ${width + 10} ${height * 0.34}`} />
              </g>
              <path
                transform={`translate(${width / 2} ${height * 0.49}) scale(${Math.min(width, height) / 150}) translate(-120 -66)`}
                fill={COLORS.lineStrong}
                d="M120 40 L122.7 50.6 L140.3 64.7 L140.3 69.3 L122.7 63.1 L122.7 80.3 L129.4 86.5 L129.4 89.2 L121.4 84.9 L120.6 92 L119.4 92 L118.6 84.9 L110.6 89.2 L110.6 86.5 L117.3 80.3 L117.3 63.1 L99.7 69.3 L99.7 64.7 L117.3 50.6 Z"
              />
            </svg>
            <span style={{ position: 'absolute', bottom: 8, left: 10, fontSize: 10.5, color: COLORS.textDim, letterSpacing: '0.06em' }}>
              TYPE PHOTO
            </span>
          </>
        )}
      </div>
      {hasPhoto && (info?.author || info?.license) && (
        <div style={{ fontSize: 11.5, color: COLORS.textMuted, lineHeight: 1.55, maxWidth: width }}>
          Photo: Wikimedia Commons{info.author ? ' — ' + info.author : ''}
          {info.license ? ', ' + info.license : ''}
        </div>
      )}
    </div>
  );
}
