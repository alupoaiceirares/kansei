import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { MiniPlayerComponent, NowPlayingTrack } from '../../shared/mini-player/mini-player';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, CommentResponse, GenreTag, TrackDetail } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { formatDuration } from '../../shared/format';

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
  imports: [RouterLink, DatePipe, AppHeaderComponent, MiniPlayerComponent, WirehoodWavesComponent],
  templateUrl: './track-detail.html',
  styleUrl: './track-detail.css',
})
export class TrackDetailPage {
  private api = inject(WirehoodApi);
  protected auth = inject(AuthService);
  private router = inject(Router);

  protected formatDuration = formatDuration;
  protected trackId: string;

  protected track = signal<TrackDetail | null>(null);
  protected genreTags = signal<GenreTag[]>([]);
  protected votedGenreIds = signal<Set<string>>(new Set());
  protected comments = signal<CommentNode[]>([]);

  protected playing = signal(false);
  protected videoOpen = signal(false);
  protected confirmDelete = signal(false);
  protected editingMetadata = signal(false);
  protected editTitle = signal('');
  protected editArtist = signal('');
  protected editExtra = signal('');

  protected commentDraft = signal('');
  protected replyingTo = signal<string | null>(null);
  protected replyDraft = signal('');
  protected editingCommentId = signal<string | null>(null);
  protected editCommentDraft = signal('');

  protected nowPlaying = signal<NowPlayingTrack | null>(null);

  @ViewChild('thumbnailInput') private thumbnailInput?: ElementRef<HTMLInputElement>;

  constructor(route: ActivatedRoute) {
    this.trackId = route.snapshot.paramMap.get('id') ?? '';
    this.reload();
    this.loadComments();
  }

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  private reload(): void {
    this.api.track(this.trackId).subscribe({ next: (track) => this.track.set(track) });
    this.api.genreTagsFor(this.trackId).subscribe({ next: (tags) => this.genreTags.set(tags) });
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

  protected togglePlay(): void {
    this.playing.update((v) => !v);
  }

  protected playFormat(format: TrackDetail['formats'][number]): void {
    const track = this.track();
    if (!track) return;
    if (format.format.toLowerCase() === 'mp4') {
      this.videoOpen.set(true);
    } else {
      this.nowPlaying.set({ title: track.title, artist: track.artist });
      this.playing.set(true);
    }
  }

  protected closeVideo(): void {
    this.videoOpen.set(false);
  }

  protected toggleFav(format: TrackDetail['formats'][number]): void {
    const call = format.favorited ? this.api.unfavoriteFormat(format.id) : this.api.favoriteFormat(format.id);
    call.subscribe({
      next: () => {
        this.track.update((t) => (t ? { ...t, formats: t.formats.map((f) => (f.id === format.id ? { ...f, favorited: !f.favorited } : f)) } : t));
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
