package org.kansei.tailwind.dto;

import org.kansei.tailwind.model.AircraftPhoto;
import org.kansei.tailwind.model.PhotoOrigin;

/**
 * Attribution for an aircraft photo, shown under it. hasPhoto false means the placeholder is served
 * and every other field is null. author and license are plain text.
 */
public record PhotoInfoResponse(boolean hasPhoto, Long aircraftTypeId, PhotoOrigin origin, String title, String author, String license, String sourceUrl) {

    public static PhotoInfoResponse none() {
        return new PhotoInfoResponse(false, null, null, null, null, null, null);
    }

    public static PhotoInfoResponse of(AircraftPhoto p) {
        return new PhotoInfoResponse(true, p.getAircraftTypeId(), p.getOrigin(), p.getTitle(), p.getAuthor(), p.getLicense(), p.getSourceUrl());
    }
}
