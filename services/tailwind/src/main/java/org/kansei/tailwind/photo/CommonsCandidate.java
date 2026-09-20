package org.kansei.tailwind.photo;

/**
 * A Wikimedia Commons image an admin can pick. thumbUrl is the resized copy we download, author and license
 * are plain text with the HTML stripped.
 */
public record CommonsCandidate(
        String title,
        String thumbUrl,
        String pageUrl,
        String author,
        String license,
        int width,
        int height,
        String mime
) {
}
