import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { optInGuard } from './core/opt-in.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'landing' },
  { path: 'landing', canActivate: [authGuard], loadComponent: () => import('./pages/landing/landing').then((m) => m.LandingPage) },
  { path: 'opt-in', canActivate: [authGuard], loadComponent: () => import('./pages/opt-in/opt-in').then((m) => m.OptInPage) },
  {
    path: 'home',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/home/home').then((m) => m.HomePage),
  },
  {
    path: 'search',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/search/search').then((m) => m.SearchPage),
  },
  {
    path: 'library',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/library/library').then((m) => m.LibraryPage),
  },
  {
    path: 'favorites',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/favorites/favorites').then((m) => m.FavoritesPage),
  },
  {
    path: 'playlists',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/playlists/playlists').then((m) => m.PlaylistsPage),
  },
  {
    path: 'song-of-the-day',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/song-of-the-day/song-of-the-day').then((m) => m.SongOfTheDayPage),
  },
  {
    path: 'account',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/account/account').then((m) => m.AccountPage),
  },
  {
    path: 'admin/thumbnails',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/admin-thumbnails/admin-thumbnails').then((m) => m.AdminThumbnailsPage),
  },
  {
    path: 'admin/genres',
    canActivate: [authGuard, optInGuard],
    loadComponent: () => import('./pages/admin-genres/admin-genres').then((m) => m.AdminGenresPage),
  },
];
