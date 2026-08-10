package org.kansei.wirehood.graphql;

import java.util.UUID;

// Carries the resolved userId from musicProfile() to each field's own @SchemaMapping resolver, no DB work happens until a field actually asks for it
record MusicProfileRoot(UUID userId) {
}
