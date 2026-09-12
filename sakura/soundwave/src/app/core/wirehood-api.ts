import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { CONTROL_TOWER_URL } from './config';

export interface SearchResult {
  videoId: string;
  title: string;
  channelTitle: string;
  thumbnailUrl: string;
  durationSeconds: number;
}

export interface ParsedTitle {
  artist: string;
  title: string;
  extraInfo: string;
}

export interface LibraryItem {
  trackId: string;
  title: string;
  artist: string;
  extraInfo: string;
  durationSeconds: number;
  hasThumbnail: boolean;
  formats: string[];
  addedAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SongOfDay {
  day: string;
  trackId: string;
  title: string;
  artist: string;
  extraInfo: string;
  durationSeconds: number;
}

export interface FriendRequest {
  userId: string;
  username: string;
  direction: 'INCOMING' | 'OUTGOING';
  requestedAt: string;
}

export interface Friend {
  userId: string;
  username: string;
  since: string;
}

export type FriendRelation = 'NONE' | 'PENDING_OUTGOING' | 'PENDING_INCOMING' | 'FRIENDS';

export interface FriendSearchResult {
  userId: string;
  username: string;
  relation: FriendRelation;
}

export interface Genre {
  id: string;
  name: string;
}

export interface TrackFormat {
  id: string;
  format: string;
  quality: string;
  status: string;
  fileSizeBytes: number;
  playCount: number | null;
  favorited: boolean | null;
}

export interface TrackDetail {
  id: string;
  youtubeVideoId: string;
  title: string;
  artist: string;
  extraInfo: string | null;
  durationSeconds: number;
  hasThumbnail: boolean;
  visible: boolean;
  formats: TrackFormat[];
}

export interface GenreTag {
  genreId: string;
  genreName: string;
  votes: number;
}

export interface CommentResponse {
  id: string;
  trackId: string;
  userId: string;
  username: string;
  parentCommentId: string | null;
  body: string;
  createdAt: string;
  editedAt: string | null;
  deleted: boolean;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
  totalElements: number;
}

export interface FavoriteItem {
  trackFormatId: string;
  trackId: string;
  title: string;
  artist: string;
  extraInfo: string | null;
  format: string;
  quality: string;
  favoritedAt: string;
}

export interface Playlist {
  id: string;
  ownerId: string;
  ownerUsername: string | null;
  name: string;
  shared: boolean;
  createdAt: string;
  trackCount: number;
}

export interface PlaylistTrack {
  trackId: string;
  title: string;
  artist: string;
  extraInfo: string | null;
  durationSeconds: number;
  position: number;
}

export interface GenreBreakdown {
  genreId: string;
  genreName: string;
  trackCount: number;
  percentage: number;
}

export interface MusicProfile {
  totalTracksSaved: number;
  genreBreakdown: GenreBreakdown[];
  mostDownloadedArtist: string | null;
  playlistsOwned: number;
  playlistsCollaborated: number;
  friendCount: number;
  totalPlays: number;
}

export type ThumbnailSubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ThumbnailSubmission {
  id: string;
  trackId: string;
  submittedBy: string;
  status: ThumbnailSubmissionStatus;
  submittedAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

export interface Collaborator {
  userId: string;
  username: string;
  addedAt: string;
}

export interface PlaylistDetail {
  id: string;
  ownerId: string;
  ownerUsername: string;
  name: string;
  shared: boolean;
  createdAt: string;
  tracks: PlaylistTrack[];
  collaborators: Collaborator[];
}

/** Thin wrapper over control-tower's /wirehood/**, auth header is attached by authInterceptor. */
@Injectable({ providedIn: 'root' })
export class WirehoodApi {
  private http = inject(HttpClient);

  optIn() {
    return this.http.post<{ userId: string; role: string; joinedAt: string; enabled: boolean }>(
      `${CONTROL_TOWER_URL}/wirehood/users/opt-in`,
      {},
    );
  }

  search(q: string) {
    return this.http.get<SearchResult[]>(`${CONTROL_TOWER_URL}/wirehood/search`, { params: new HttpParams().set('q', q) });
  }

  parseTitle(title: string) {
    return this.http.get<ParsedTitle>(`${CONTROL_TOWER_URL}/wirehood/search/parse-title`, { params: new HttpParams().set('title', title) });
  }

  library(page = 0, size = 20) {
    return this.http.get<Page<LibraryItem>>(`${CONTROL_TOWER_URL}/wirehood/library`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  songOfTheDay() {
    return this.http.get<SongOfDay>(`${CONTROL_TOWER_URL}/wirehood/song-of-the-day`);
  }

  friendRequests() {
    return this.http.get<FriendRequest[]>(`${CONTROL_TOWER_URL}/wirehood/friends/requests`);
  }

  friends() {
    return this.http.get<Friend[]>(`${CONTROL_TOWER_URL}/wirehood/friends`);
  }

  searchFriends(query: string, limit = 20) {
    return this.http.get<FriendSearchResult[]>(`${CONTROL_TOWER_URL}/wirehood/friends/search`, {
      params: new HttpParams().set('query', query).set('limit', limit),
    });
  }

  sendFriendRequest(userId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/friends/requests`, { userId });
  }

  acceptFriendRequest(otherUserId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/friends/requests/${otherUserId}/accept`, {});
  }

  removeFriendOrRequest(otherUserId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/friends/${otherUserId}`);
  }

  genres() {
    return this.http.get<Genre[]>(`${CONTROL_TOWER_URL}/wirehood/genres`);
  }

  tagGenres(trackId: string, genreIds: string[]) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/genre-tags`, { genreIds });
  }

  track(trackId: string) {
    return this.http.get<TrackDetail>(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}`);
  }

  genreTagsFor(trackId: string) {
    return this.http.get<GenreTag[]>(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/genre-tags`);
  }

  favoriteFormat(trackFormatId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/track-formats/${trackFormatId}/favorite`, {});
  }

  unfavoriteFormat(trackFormatId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/track-formats/${trackFormatId}/favorite`);
  }

  comments(trackId: string, cursor?: string, size = 50) {
    let params = new HttpParams().set('size', size);
    if (cursor) params = params.set('cursor', cursor);
    return this.http.get<CursorPage<CommentResponse>>(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/comments`, { params });
  }

  postComment(trackId: string, body: string, parentCommentId: string | null) {
    return this.http.post<CommentResponse>(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/comments`, { body, parentCommentId });
  }

  editComment(commentId: string, body: string) {
    return this.http.patch<CommentResponse>(`${CONTROL_TOWER_URL}/wirehood/comments/${commentId}`, { body });
  }

  deleteComment(commentId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/comments/${commentId}`);
  }

  submitThumbnail(trackId: string, file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/thumbnail-submissions`, form);
  }

  editTrackMetadata(trackId: string, body: { title: string; artist: string; extraInfo: string | null }) {
    return this.http.patch(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}`, body);
  }

  setTrackVisible(trackId: string, visible: boolean) {
    return this.http.patch(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/visible`, { visible });
  }

  hardDeleteTrack(trackId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}`);
  }

  removeGenreTag(trackId: string, genreId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/genre-tags/${genreId}`);
  }

  exportData() {
    return this.http.get(`${CONTROL_TOWER_URL}/wirehood/export`, { responseType: 'blob' });
  }

  favorites(page = 0, size = 20) {
    return this.http.get<Page<FavoriteItem>>(`${CONTROL_TOWER_URL}/wirehood/favorites`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  myPlaylists() {
    return this.http.get<Playlist[]>(`${CONTROL_TOWER_URL}/wirehood/playlists/mine`);
  }

  createPlaylist(name: string, shared: boolean) {
    return this.http.post<Playlist>(`${CONTROL_TOWER_URL}/wirehood/playlists`, { name, shared });
  }

  playlist(playlistId: string) {
    return this.http.get<PlaylistDetail>(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}`);
  }

  updatePlaylist(playlistId: string, name: string, shared: boolean) {
    return this.http.patch<Playlist>(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}`, { name, shared });
  }

  deletePlaylist(playlistId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}`);
  }

  addTrackToPlaylist(playlistId: string, trackId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}/tracks`, { trackId });
  }

  removeTrackFromPlaylist(playlistId: string, trackId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}/tracks/${trackId}`);
  }

  reorderPlaylistTracks(playlistId: string, trackIds: string[]) {
    return this.http.put(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}/tracks/order`, { trackIds });
  }

  addCollaborator(playlistId: string, userId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}/collaborators`, { userId });
  }

  removeCollaborator(playlistId: string, userId: string) {
    return this.http.delete(`${CONTROL_TOWER_URL}/wirehood/playlists/${playlistId}/collaborators/${userId}`);
  }

  adminThumbnailSubmissions(status: ThumbnailSubmissionStatus, page = 0, size = 20) {
    return this.http.get<Page<ThumbnailSubmission>>(`${CONTROL_TOWER_URL}/wirehood/admin/thumbnail-submissions`, {
      params: new HttpParams().set('status', status).set('page', page).set('size', size),
    });
  }

  adminThumbnailFile(submissionId: string) {
    return this.http.get(`${CONTROL_TOWER_URL}/wirehood/admin/thumbnail-submissions/${submissionId}/file`, { responseType: 'blob' });
  }

  adminApproveThumbnail(submissionId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/admin/thumbnail-submissions/${submissionId}/approve`, {});
  }

  adminRejectThumbnail(submissionId: string) {
    return this.http.post(`${CONTROL_TOWER_URL}/wirehood/admin/thumbnail-submissions/${submissionId}/reject`, {});
  }

  musicProfile() {
    const query = `query {
      musicProfile {
        totalTracksSaved
        mostDownloadedArtist
        playlistsOwned
        playlistsCollaborated
        friendCount
        totalPlays
        genreBreakdown { genreId genreName trackCount percentage }
      }
    }`;
    return this.http
      .post<{ data: { musicProfile: MusicProfile } }>(`${CONTROL_TOWER_URL}/wirehood/graphql`, { query })
      .pipe(map((res) => res.data.musicProfile));
  }
}
