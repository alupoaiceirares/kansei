import { feature } from 'topojson-client';
import type { Topology, GeometryCollection } from 'topojson-specification';
import type { Feature, Geometry } from 'geojson';

export type CountryFeature = Feature<Geometry, { name: string }> & { id?: string | number };

// Natural Earth 110m geometry, vendored into public/ rather than pulled from a CDN at runtime.
const ATLAS_URL = '/countries-110m.json';

let pending: Promise<CountryFeature[]> | null = null;

/** Fetched and parsed once per page load, every map shares the result. */
export function loadCountries(): Promise<CountryFeature[]> {
  if (!pending) {
    pending = fetch(ATLAS_URL)
      .then((response) => {
        if (!response.ok) throw new Error('atlas ' + response.status);
        return response.json();
      })
      .then((topology: Topology<{ countries: GeometryCollection<{ name: string }> }>) => {
        const collection = feature(topology, topology.objects.countries);
        return collection.features as CountryFeature[];
      })
      .catch((error) => {
        pending = null;
        throw error;
      });
  }
  return pending;
}

export function countryIdOf(feature: CountryFeature): string {
  return String(feature.id ?? '');
}
