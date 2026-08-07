--liquibase formatted sql

--changeset kansei:001-create-tracks-table
-- One row per canonical YouTube video, shared by every user (platform-wide dedup)
CREATE TABLE tracks (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         youtube_video_id VARCHAR(20) NOT NULL UNIQUE,
                         title VARCHAR(255) NOT NULL,
                         artist VARCHAR(255),
                         extra_info VARCHAR(255),
                         duration_seconds INTEGER NOT NULL,
                         thumbnail_path VARCHAR(500),
                         status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'DOWNLOADING', 'READY', 'FAILED')),
                         visible BOOLEAN NOT NULL DEFAULT TRUE,
                         details JSONB,
                         created_at TIMESTAMP NOT NULL,
                         updated_at TIMESTAMP NOT NULL
);

--changeset kansei:002-create-track-formats-table
-- One row per format/quality combo actually downloaded for a track (e.g. mp3/320kbps, mp4/1080p)
CREATE TABLE track_formats (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
                                format VARCHAR(10) NOT NULL,
                                quality VARCHAR(20) NOT NULL,
                                file_path VARCHAR(500) NOT NULL,
                                file_size_bytes BIGINT NOT NULL
);

CREATE INDEX idx_track_formats_track_id ON track_formats (track_id);

--changeset kansei:003-create-user-library-table
-- "My saved tracks, a pure composite-key join table (PK user_id+track_id, no surrogate id) - given a surrogate id + UNIQUE constraint instead, since Spring Data R2DBC needs a single @Id column to tell insert from update;
-- the UNIQUE constraint preserves the same "one row per (user, track) pair" guarantee either way
CREATE TABLE user_library (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL,
                               track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
                               added_at TIMESTAMP NOT NULL,
                               UNIQUE (user_id, track_id)
);

CREATE INDEX idx_user_library_user_id ON user_library (user_id);

--changeset kansei:004-create-download-requests-table
-- Per-user "pending"/"failed" tracking - separate from tracks.status (shared/global) since multiple users can each be waiting on the same in-flight download
CREATE TABLE download_requests (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    user_id UUID NOT NULL,
                                    track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
                                    requested_at TIMESTAMP NOT NULL,
                                    acknowledged_at TIMESTAMP
);

CREATE INDEX idx_download_requests_user_id ON download_requests (user_id);
CREATE INDEX idx_download_requests_track_id ON download_requests (track_id);

--changeset kansei:005-create-wirehood-users-table
-- Marks which shieldwall users have actually opted into wirehood - not auto-created on login, inserted when the user confirms the frontend's "would you like to use wirehood?" popup
-- user_id IS the PK here (unlike user_library/download_requests) since there's naturally only one row per user, no composite-key workaround needed
CREATE TABLE wirehood_users (
                                 user_id UUID PRIMARY KEY,
                                 role VARCHAR(20) NOT NULL DEFAULT 'USER'
                                     CHECK (role IN ('USER', 'ADMIN')),
                                 joined_at TIMESTAMP NOT NULL
);

--changeset kansei:006-create-genres-table
-- Fixed, seeded list - not user-created, keeps tagging consistent and avoids genre-name drift/duplicates (see WIREHOOD_PLAN.md's Genres + music profile section)
CREATE TABLE genres (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO genres (name) VALUES
    ('Pop'), ('Rock'), ('Hip-Hop'), ('R&B'), ('Electronic'), ('Jazz'), ('Classical'),
    ('Country'), ('Metal'), ('Folk'), ('Reggae'), ('Blues'), ('Punk'), ('Indie'), ('Latin'),
    ('K-Pop'), ('J-Pop'), ('Funk'), ('Soul'), ('Disco'), ('House'), ('Techno'), ('Trance'),
    ('Dubstep'), ('Ambient'), ('Lo-fi'), ('Gospel'), ('World'), ('Soundtrack'), ('Alternative'),
    ('Synthpop'), ('Drum & Bass'), ('EDM'), ('Grunge'), ('Hard Rock'), ('Prog Rock'), ('Trap'),
    ('Emo'), ('Ska'), ('Reggaeton'), ('Bossa Nova'), ('Flamenco'), ('New Age'), ('Chillout'),
    ('Vaporwave');

--changeset kansei:007-create-track-genre-tags-table
-- Crowd-tagging vote, not a plain join - one vote per (track, genre, user) enforced by the PK, so a track's "real" genre(s) are whichever tags accumulate the most distinct-user votes over time. Self-heals with usage (folksonomy-style), no moderation system needed to start
CREATE TABLE track_genre_tags (
                                   track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
                                   genre_id UUID NOT NULL REFERENCES genres (id) ON DELETE CASCADE,
                                   user_id UUID NOT NULL,
                                   tagged_at TIMESTAMP NOT NULL,
                                   PRIMARY KEY (track_id, genre_id, user_id)
);

--changeset kansei:008-create-track-comments-table
-- parent_comment_id (nullable, self-FK) gives threaded replies at arbitrary depth via a self-join - no separate "replies" table needed
-- Soft delete (deleted_at) not hard delete - if a parent with live replies were hard-deleted the thread would break; render as "[deleted]" at read time instead, replies stay visible
CREATE TABLE track_comments (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 track_id UUID NOT NULL REFERENCES tracks (id) ON DELETE CASCADE,
                                 user_id UUID NOT NULL,
                                 parent_comment_id UUID REFERENCES track_comments (id),
                                 body VARCHAR(2000) NOT NULL,
                                 created_at TIMESTAMP NOT NULL,
                                 edited_at TIMESTAMP,
                                 deleted_at TIMESTAMP
);

CREATE INDEX idx_track_comments_track_id ON track_comments (track_id);
CREATE INDEX idx_track_comments_parent_comment_id ON track_comments (parent_comment_id);
