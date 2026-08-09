package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackFormat;

public record FormatExport(String format, String quality) {
    public static FormatExport from(TrackFormat trackFormat) {
        return new FormatExport(trackFormat.getFormat(), trackFormat.getQuality());
    }
}
