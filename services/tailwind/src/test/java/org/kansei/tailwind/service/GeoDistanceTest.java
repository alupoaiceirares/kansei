package org.kansei.tailwind.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoDistanceTest {

    @Test
    void frankfurtToNewYorkIsCloseToTheProviderFigure() {
        // AeroDataBox reported 6204.69 km, the mean-radius haversine gives about 6189, within 0.3 percent
        double km = GeoDistance.haversineKm(50.026706, 8.55835, 40.639447, -73.779317);

        assertThat(km).isBetween(6180.0, 6200.0);
    }

    @Test
    void samePointIsZeroAndDistanceIsSymmetric() {
        assertThat(GeoDistance.haversineKm(45.0, 25.0, 45.0, 25.0)).isZero();
        assertThat(GeoDistance.haversineKm(45.0, 25.0, -33.9, 151.2)).isEqualTo(GeoDistance.haversineKm(-33.9, 151.2, 45.0, 25.0));
    }
}
