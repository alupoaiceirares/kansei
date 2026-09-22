// Mirrors the tailwind DTOs. Only the fields the screens actually read are declared.

export type Visibility = 'PUBLIC' | 'FRIENDS' | 'PRIVATE';

export type TailwindUser = {
  userId: string;
  username: string | null;
  joinedAt: string;
  enabled: boolean;
  defaultVisibility: Visibility;
  role: string | null;
};

export type FriendRequest = {
  userId: string;
  username: string | null;
  direction: 'INCOMING' | 'OUTGOING';
  requestedAt: string;
};

export type Friend = {
  userId: string;
  username: string | null;
  friendsSince: string;
};
