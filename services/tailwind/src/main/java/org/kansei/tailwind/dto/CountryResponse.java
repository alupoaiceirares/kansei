package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.Country;

public record CountryResponse(String code, String name, String continent) {

    public static CountryResponse of(Country c) {
        return new CountryResponse(c.getCode(), c.getName(), c.getContinent());
    }
}
