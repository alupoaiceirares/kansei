package org.kansei.wirehood.parser;

/**
 * A record carrying the parser's output (artist, title, extraInfo) back through the controller as JSON
 * Spring serializes the record to {"artist": ..., "title": ..., "extraInfo": ...} for the frontend's confirm popup
 */
public record ParsedTitle(String artist, String title, String extraInfo) {
}
