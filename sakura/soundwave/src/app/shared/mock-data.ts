import { DownloadQueueItem } from './app-header/app-header';

/** Stand-in for a real GET /wirehood/downloads/pending response until the API client is wired up. */
export const MOCK_DOWNLOAD_QUEUE: DownloadQueueItem[] = [
  { id: 'q1', title: 'Halogen Hymn — MP4', status: 'pending', statusText: 'Converting · 62%' },
  { id: 'q2', title: 'Tin Roof Static — MP3', status: 'failed', statusText: 'Source unavailable' },
  { id: 'q3', title: 'Copper Line — MP3', status: 'ready', statusText: 'Ready · added to library' },
];
