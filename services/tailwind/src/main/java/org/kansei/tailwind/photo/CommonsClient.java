package org.kansei.tailwind.photo;

import java.util.List;
import java.util.Optional;

/**
 * Wikimedia Commons image search, used only by admins picking an aircraft type photo.
 */
public interface CommonsClient {

    /**
     * Best matches first, only JPEG and PNG.
     *
     * @throws CommonsUnavailableException when Commons cannot be reached or answers with an error
     */
    List<CommonsCandidate> search(String query, int limit);

    /**
     * One file by its exact title ("File:..."), so a pick is always re-read from Commons and never trusted from the client.
     */
    Optional<CommonsCandidate> findByTitle(String title);
}
