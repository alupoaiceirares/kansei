package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.AircraftType;
import org.kansei.tailwind.model.BodyType;
import org.kansei.tailwind.model.EngineType;

public record AircraftTypeResponse(
        Long id,
        String icaoCode,
        String manufacturer,
        String model,
        String name,
        String family,
        BodyType bodyType,
        EngineType engineType,
        Short engineCount,
        String wakeCategory
) {

    public static AircraftTypeResponse of(AircraftType t) {
        return new AircraftTypeResponse(t.getId(), t.getIcaoCode(), t.getManufacturer(), t.getModel(), t.getName(), t.getFamily(),
                t.getBodyType(), t.getEngineType(), t.getEngineCount(), t.getWakeCategory());
    }
}
