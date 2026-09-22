import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { ArrivalPage } from './pages/Arrival';
import { DashboardPage } from './pages/Dashboard';
import { LandingPage } from './pages/Landing';
import { OptInPage } from './pages/OptIn';
import { Placeholder } from './pages/Placeholder';

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
            <Placeholder title="Add flight" />
          </RequireAuth>
        }
      />
      <Route
        path="/add/confirm"
        element={
          <RequireAuth>
            <Placeholder title="Confirm flight" />
          </RequireAuth>
        }
      />
      <Route
        path="/add/manual"
        element={
          <RequireAuth>
            <Placeholder title="Add a flight by hand" />
          </RequireAuth>
        }
      />
      <Route
        path="/flights"
        element={
          <RequireAuth>
            <Placeholder title="My flights" />
          </RequireAuth>
        }
      />
      <Route
        path="/flights/:userFlightId"
        element={
          <RequireAuth>
            <Placeholder title="Flight" />
          </RequireAuth>
        }
      />
      <Route
        path="/journeys"
        element={
          <RequireAuth>
            <Placeholder title="Journeys" />
          </RequireAuth>
        }
      />
      <Route
        path="/journeys/:journeyId"
        element={
          <RequireAuth>
            <Placeholder title="Journey" />
          </RequireAuth>
        }
      />
      <Route
        path="/map"
        element={
          <RequireAuth>
            <Placeholder title="Map" />
          </RequireAuth>
        }
      />
      <Route
        path="/stats"
        element={
          <RequireAuth>
            <Placeholder title="Stats" />
          </RequireAuth>
        }
      />
      <Route
        path="/friends"
        element={
          <RequireAuth>
            <Placeholder title="Friends" />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <Placeholder title="My profile" />
          </RequireAuth>
        }
      />
      <Route
        path="/users/:userId"
        element={
          <RequireAuth>
            <Placeholder title="Profile" />
          </RequireAuth>
        }
      />
      <Route
        path="/admin"
        element={
          <RequireAuth>
            <Placeholder title="Admin" />
          </RequireAuth>
        }
      />
      <Route
        path="/recap"
        element={
          <RequireAuth>
            <Placeholder title="Yearly recap" />
          </RequireAuth>
        }
      />

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
