import { Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Subscription, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, CommentResponse, Genre, GenreTag, Playlist, TrackDetail } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { PlaybackService } from '../../core/playback';
import { formatDuration, saveBlob, trackThumbnailUrl } from '../../shared/format';

interface CommentNode extends CommentResponse {
  depth: number;
}

const PALETTE_GREEN = { threadLine: 'rgba(18,183,107,0.3)', avatarBg: 'rgba(18,183,107,0.14)', avatarRing: 'rgba(18,183,107,0.3)', initialColor: '#12B76B' };
const PALETTE_RED = { threadLine: 'rgba(226,58,58,0.3)', avatarBg: 'rgba(226,58,58,0.14)', avatarRing: 'rgba(226,58,58,0.3)', initialColor: '#E23A3A' };

function buildTree(items: CommentResponse[]): CommentNode[] {
  const byParent = new Map<string | null, CommentResponse[]>();
  for (const c of items) {
    const list = byParent.get(c.parentCommentId) ?? [];
    list.push(c);
    byParent.set(c.parentCommentId, list);
  }
  const out: CommentNode[] = [];
  const walk = (parentId: string | null, depth: number) => {
    for (const c of byParent.get(parentId) ?? []) {
      out.push({ ...c, depth });
      walk(c.id, depth + 1);
    }
  };
  walk(null, 0);
  return out;
}

/** Track detail: per-format play/download/favorite, genre voting, admin controls, threaded comments. */
@Component({
  selector: 'wh-track-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './track-detail.html',
  styleUrl: './track-detail.css',
})
export class TrackDetailPage implements OnDestroy {
  private api = inject(WirehoodApi);
  protected auth = inject(AuthService);
  private router = inject(Router);
  protected playback = inject(PlaybackService);

  protected formatDuration = formatDuration;
  protected thumbnailUrl = trackThumbnailUrl;
  protected trackId: string;

  // Not the shared hideOnError - this page reuses a single <img> element across track navigations
  // (Angular just rebinds [src] on the same DOM node, it doesn't recreate it), so an inline style
  // set imperatively by a previous track's 404 would silently persist onto the next track even if
  // its thumbnail loads fine. A signal reset in resetForNewTrack() avoids that staleness.
  protected thumbFailed = signal(false);

  protected onThumbError(): void {
    this.thumbFailed.set(true);
  }

  protected track = signal<TrackDetail | null>(null);
  protected genreTags = signal<GenreTag[]>([]);
  protected votedGenreIds = signal<Set<string>>(new Set());
  protected comments = signal<CommentNode[]>([]);

  protected allGenres = signal<Genre[]>([]);
  protected addGenreOpen = signal(false);
  protected proposingGenre = signal(false);
  protected newGenreName = signal('');
  protected proposeMessage = signal<string | null>(null);

  // Every platform genre this track has no tag row for yet - lets a user add the first-ever tag
  // to an untagged track, not just vote on genres someone else already tagged (genreTags() only
  // ever contains genres with at least one existing vote, so an untagged track showed nothing to click)
  protected untaggedGenres = computed(() => {
    const tagged = new Set(this.genreTags().map((t) => t.genreId));
    return this.allGenres().filter((g) => !tagged.has(g.id));
  });

  protected videoOpen = signal(false);
  protected videoUrl = signal<string | null>(null);
  protected videoLoading = signal(false);
  protected confirmDelete = signal(false);
  protected editingMetadata = signal(false);
  protected editTitle = signal('');
  protected editArtist = signal('');
  protected editExtra = signal('');

  protected playlistPickerOpen = signal(false);
  protected myPlaylists = signal<Playlist[]>([]);
  protected addedPlaylistIds = signal<Set<string>>(new Set());
  protected creatingPlaylist = signal(false);
  protected newPlaylistName = signal('');

  protected commentDraft = signal('');
  protected replyingTo = signal<string | null>(null);
  protected replyDraft = signal('');
  protected editingCommentId = signal<string | null>(null);
  protected editCommentDraft = signal('');

  @ViewChild('thumbnailInput') private thumbnailInput?: ElementRef<HTMLInputElement>;

  private paramSub: Subscription;

  constructor(route: ActivatedRoute) {
    this.trackId = route.snapshot.paramMap.get('id') ?? '';
    // Subscribed, not just snapshot.paramMap.get() once - Angular reuses this same component
    // instance across /track/:id -> /track/:id navigations (same route config), so the constructor
    // only ever runs for the first track opened. Without this, every field here stayed pinned to
    // whichever track was opened first (comments included) no matter which track you navigated to next.
    this.paramSub = route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (!id) return;
      this.trackId = id;
      this.resetForNewTrack();
      this.reload();
      this.loadComments();
    });
  }

  ngOnDestroy(): void {
    this.paramSub.unsubscribe();
    this.releaseVideo();
  }

  // Every signal that's scoped to "the track currently on screen" - cleared on navigation to a
  // different track so nothing from the previous track flashes or lingers while the new one loads
  private resetForNewTrack(): void {
    this.releaseVideo();
    this.track.set(null);
    this.thumbFailed.set(false);
    this.genreTags.set([]);
    this.votedGenreIds.set(new Set());
    this.addGenreOpen.set(false);
    this.proposingGenre.set(false);
    this.proposeMessage.set(null);
    this.comments.set([]);
    this.videoOpen.set(false);
    this.videoLoading.set(false);
    this.confirmDelete.set(false);
    this.editingMetadata.set(false);
    this.playlistPickerOpen.set(false);
    this.addedPlaylistIds.set(new Set());
    this.creatingPlaylist.set(false);
    this.commentDraft.set('');
    this.replyingTo.set(null);
    this.replyDraft.set('');
    this.editingCommentId.set(null);
    this.editCommentDraft.set('');
  }

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  private reload(): void {
    this.api.track(this.trackId).subscribe({ next: (track) => this.track.set(track) });
    this.api.genreTagsFor(this.trackId).subscribe({ next: (tags) => this.genreTags.set(tags) });
    if (this.allGenres().length === 0) {
      this.api.genres().subscribe({ next: (genres) => this.allGenres.set(genres) });
    }
  }

  private loadComments(): void {
    this.api.comments(this.trackId).subscribe({
      next: (page) => this.comments.set(buildTree(page.items)),
      error: () => {},
    });
  }

  protected paletteFor(index: number) {
    return index % 2 === 0 ? PALETTE_GREEN : PALETTE_RED;
  }

  protected isMine(comment: CommentResponse): boolean {
    return comment.userId === this.auth.getUserId();
  }

  protected playFormat(format: TrackDetail['formats'][number]): void {
    const track = this.track();
    if (!track) return;
    if (format.format.toLowerCase() === 'mp4') {
      this.openVideo();
    } else {
      this.playback.playSingle({ trackId: track.id, title: track.title, artist: track.artist });
    }
  }

  private openVideo(): void {
    this.videoOpen.set(true);
    this.videoLoading.set(true);
    this.api.downloadFile(this.trackId, 'mp4').subscribe({
      next: (blob) => {
        this.releaseVideo();
        this.videoUrl.set(URL.createObjectURL(blob));
        this.videoLoading.set(false);
      },
      error: () => {
        this.videoLoading.set(false);
        this.videoOpen.set(false);
      },
    });
  }

  protected closeVideo(): void {
    this.videoOpen.set(false);
    this.releaseVideo();
  }

  private releaseVideo(): void {
    const url = this.videoUrl();
    if (url) URL.revokeObjectURL(url);
    this.videoUrl.set(null);
  }

  protected downloadFormat(format: TrackDetail['formats'][number]): void {
    const track = this.track();
    if (!track) return;
    this.api
      .downloadFile(this.trackId, format.format)
      .subscribe({ next: (blob) => saveBlob(blob, `${track.artist} - ${track.title}.${format.format}`) });
  }

  protected toggleFav(format: TrackDetail['formats'][number]): void {
    const call = format.favorited ? this.api.unfavoriteFormat(format.id) : this.api.favoriteFormat(format.id);
    call.subscribe({
      next: () => {
        this.track.update((t) => (t ? { ...t, formats: t.formats.map((f) => (f.id === format.id ? { ...f, favorited: !f.favorited } : f)) } : t));
      },
    });
  }

  protected openPlaylistPicker(): void {
    this.creatingPlaylist.set(false);
    this.newPlaylistName.set('');
    this.playlistPickerOpen.set(true);
    // Independent calls on purpose - a failure fetching which playlists already have this track
    // shouldn't also blank the playlist list itself, it should just fall back to "none checked"
    this.api.myPlaylists().subscribe({ next: (list) => this.myPlaylists.set(list) });
    this.api
      .myPlaylistIdsContaining(this.trackId)
      .pipe(catchError(() => of([])))
      .subscribe({ next: (ids) => this.addedPlaylistIds.set(new Set(ids)) });
  }

  protected closePlaylistPicker(): void {
    this.playlistPickerOpen.set(false);
  }

  // Same fix as search page's onScrimClick - a click's target is decided by where the mouse comes
  // UP, so selecting text in the "new playlist name" input and releasing past the scrim closed
  // the picker mid-selection. Only close if the press that started this click also began on the scrim.
  private scrimPressStartedOnScrim = false;

  protected onScrimMouseDown(event: MouseEvent): void {
    this.scrimPressStartedOnScrim = event.target === event.currentTarget;
  }

  protected onScrimClick(event: MouseEvent): void {
    if (this.scrimPressStartedOnScrim && event.target === event.currentTarget) {
      this.closePlaylistPicker();
    }
  }

  protected isAddedToPlaylist(playlistId: string): boolean {
    return this.addedPlaylistIds().has(playlistId);
  }

  // Toggle - clicking an already-added playlist removes the track from it instead
  protected togglePlaylist(playlistId: string): void {
    if (this.isAddedToPlaylist(playlistId)) {
      this.api.removeTrackFromPlaylist(playlistId, this.trackId).subscribe({
        next: () =>
          this.addedPlaylistIds.update((s) => {
            const next = new Set(s);
            next.delete(playlistId);
            return next;
          }),
      });
    } else {
      this.api.addTrackToPlaylist(playlistId, this.trackId).subscribe({
        next: () => this.addedPlaylistIds.update((s) => new Set(s).add(playlistId)),
      });
    }
  }

  protected startCreatePlaylist(): void {
    this.creatingPlaylist.set(true);
    this.newPlaylistName.set('');
  }

  protected cancelCreatePlaylist(): void {
    this.creatingPlaylist.set(false);
  }

  protected confirmCreatePlaylist(): void {
    const name = this.newPlaylistName().trim();
    if (!name) return;
    this.api.createPlaylist(name, false).subscribe({
      next: (playlist) => {
        this.myPlaylists.update((list) => [playlist, ...list]);
        this.creatingPlaylist.set(false);
        this.togglePlaylist(playlist.id);
      },
    });
  }

  protected vote(genreId: string): void {
    if (this.votedGenreIds().has(genreId)) return;
    this.api.tagGenres(this.trackId, [genreId]).subscribe({
      next: () => {
        this.votedGenreIds.update((s) => new Set(s).add(genreId));
        this.genreTags.update((tags) => tags.map((t) => (t.genreId === genreId ? { ...t, votes: t.votes + 1 } : t)));
      },
    });
  }

  // Admin-only moderation action, bad-faith tagging the crowd-vote hasn't corrected yet
  protected adminRemoveGenreTag(genreId: string, event: Event): void {
    event.stopPropagation();
    this.api.removeGenreTag(this.trackId, genreId).subscribe({
      next: () => {
        this.genreTags.update((tags) => tags.filter((t) => t.genreId !== genreId));
        this.votedGenreIds.update((s) => {
          const next = new Set(s);
          next.delete(genreId);
          return next;
        });
      },
    });
  }

  protected toggleAddGenre(): void {
    this.addGenreOpen.update((v) => !v);
    this.proposingGenre.set(false);
    this.proposeMessage.set(null);
  }

  // Adds the FIRST tag for this genre on this track - same endpoint as vote(), the backend takes
  // any genreId from the full catalog, vote() just never had a way to reach an untagged one
  protected addGenre(genre: Genre): void {
    this.api.tagGenres(this.trackId, [genre.id]).subscribe({
      next: () => {
        this.genreTags.update((tags) => [...tags, { genreId: genre.id, genreName: genre.name, votes: 1 }]);
        this.votedGenreIds.update((s) => new Set(s).add(genre.id));
      },
    });
  }

  protected startProposeGenre(): void {
    this.proposingGenre.set(true);
    this.newGenreName.set('');
    this.proposeMessage.set(null);
  }

  protected cancelProposeGenre(): void {
    this.proposingGenre.set(false);
  }

  protected submitGenreProposal(): void {
    const name = this.newGenreName().trim();
    if (!name) return;
    this.api.proposeGenre(name).subscribe({
      next: () => {
        this.proposingGenre.set(false);
        this.newGenreName.set('');
        this.proposeMessage.set(`"${name}" proposed, pending admin review.`);
      },
      error: (err) => {
        this.proposeMessage.set(err.status === 409 ? err.error?.message ?? 'Already proposed or already exists.' : 'Could not submit, try again.');
      },
    });
  }

  protected pickThumbnail(): void {
    this.thumbnailInput?.nativeElement.click();
  }

  protected onThumbnailSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.api.submitThumbnail(this.trackId, file).subscribe();
  }

  protected startEditMetadata(): void {
    const track = this.track();
    if (!track) return;
    this.editTitle.set(track.title);
    this.editArtist.set(track.artist);
    this.editExtra.set(track.extraInfo ?? '');
    this.editingMetadata.set(true);
  }

  protected cancelEditMetadata(): void {
    this.editingMetadata.set(false);
  }

  protected saveMetadata(): void {
    this.api.editTrackMetadata(this.trackId, { title: this.editTitle(), artist: this.editArtist(), extraInfo: this.editExtra() || null }).subscribe({
      next: () => {
        this.editingMetadata.set(false);
        this.reload();
      },
    });
  }

  protected toggleVisible(): void {
    const track = this.track();
    if (!track) return;
    this.api.setTrackVisible(this.trackId, !track.visible).subscribe({
      next: () => this.track.update((t) => (t ? { ...t, visible: !t.visible } : t)),
    });
  }

  protected askDelete(): void {
    this.confirmDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmDelete.set(false);
  }

  protected confirmHardDelete(): void {
    this.api.hardDeleteTrack(this.trackId).subscribe({
      next: () => this.router.navigateByUrl('/library'),
    });
  }

  protected postTopLevelComment(): void {
    const body = this.commentDraft().trim();
    if (!body) return;
    this.api.postComment(this.trackId, body, null).subscribe({
      next: () => {
        this.commentDraft.set('');
        this.loadComments();
      },
    });
  }

  protected startReply(commentId: string): void {
    this.replyingTo.set(commentId);
    this.replyDraft.set('');
  }

  protected cancelReply(): void {
    this.replyingTo.set(null);
  }

  protected submitReply(parentId: string): void {
    const body = this.replyDraft().trim();
    if (!body) return;
    this.api.postComment(this.trackId, body, parentId).subscribe({
      next: () => {
        this.replyingTo.set(null);
        this.loadComments();
      },
    });
  }

  protected startEditComment(comment: CommentNode): void {
    this.editingCommentId.set(comment.id);
    this.editCommentDraft.set(comment.body);
  }

  protected cancelEditComment(): void {
    this.editingCommentId.set(null);
  }

  protected submitEditComment(commentId: string): void {
    const body = this.editCommentDraft().trim();
    if (!body) return;
    this.api.editComment(commentId, body).subscribe({
      next: () => {
        this.editingCommentId.set(null);
        this.loadComments();
      },
    });
  }

  protected deleteComment(commentId: string): void {
    this.api.deleteComment(commentId).subscribe({ next: () => this.loadComments() });
  }
}
