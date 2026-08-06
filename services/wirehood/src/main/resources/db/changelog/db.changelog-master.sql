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
