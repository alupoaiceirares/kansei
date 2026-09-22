import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { ArrivalPage } from './pages/Arrival';
import { AddFlightPage } from './pages/AddFlight';
import { AddFlightConfirmPage } from './pages/AddFlightConfirm';
import { DashboardPage } from './pages/Dashboard';
import { FlightDetailPage } from './pages/FlightDetail';
import { AdminPage } from './pages/Admin';
import { FriendsPage } from './pages/Friends';
import { JourneyDetailPage } from './pages/JourneyDetail';
import { JourneysPage } from './pages/Journeys';
import { MapPage } from './pages/MapPage';
import { ProfileMinePage } from './pages/ProfileMine';
import { ProfileOtherPage } from './pages/ProfileOther';
import { RecapPage } from './pages/Recap';
import { StatsPage } from './pages/Stats';
import { ManualEntryPage } from './pages/ManualEntry';
import { MyFlightsPage } from './pages/MyFlights';
import { LandingPage } from './pages/Landing';
import { OptInPage } from './pages/OptIn';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/arrival" element={<ArrivalPage />} />
      <Route
        path="/opt-in"
        element={
          <RequireAuth allowNotOptedIn>
            <OptInPage />
          </RequireAuth>
        }
      />

      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/add"
        element={
          <RequireAuth>
            <AddFlightPage />
          </RequireAuth>
        }
      />
      <Route
        path="/add/confirm"
        element={
          <RequireAuth>
            <AddFlightConfirmPage />
          </RequireAuth>
        }
      />
      <Route
        path="/add/manual"
        element={
          <RequireAuth>
            <ManualEntryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/flights"
        element={
          <RequireAuth>
            <MyFlightsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/flights/:userFlightId"
        element={
          <RequireAuth>
            <FlightDetailPage />
          </RequireAuth>
        }
      />
      <Route
        path="/journeys"
        element={
          <RequireAuth>
            <JourneysPage />
          </RequireAuth>
        }
      />
      <Route
        path="/journeys/:journeyId"
        element={
          <RequireAuth>
            <JourneyDetailPage />
          </RequireAuth>
        }
      />
      <Route
        path="/map"
        element={
          <RequireAuth>
            <MapPage />
          </RequireAuth>
        }
      />
      <Route
        path="/stats"
        element={
          <RequireAuth>
            <StatsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/friends"
        element={
          <RequireAuth>
            <FriendsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <ProfileMinePage />
          </RequireAuth>
        }
      />
      <Route
        path="/users/:userId"
        element={
          <RequireAuth>
            <ProfileOtherPage />
          </RequireAuth>
        }
      />
      <Route
        path="/admin"
        element={
          <RequireAuth>
            <AdminPage />
          </RequireAuth>
        }
      />
      <Route
        path="/recap"
        element={
          <RequireAuth>
            <RecapPage />
          </RequireAuth>
        }
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
