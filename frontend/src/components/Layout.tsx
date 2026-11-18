import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { serverStatus, useServerStatus } from '../lib/serverStatus';
import { MuneemChatProvider } from '../lib/muneemChat';
import { MuneemWidget } from './muneem/MuneemWidget';

const LINKS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/expenses', label: 'Expenses', end: false },
  { to: '/upload', label: 'Upload', end: false },
  { to: '/rules', label: 'Rules', end: false },
  { to: '/compare', label: 'Compare', end: false },
  { to: '/muneem', label: 'Muneem', end: false },
];

function linkClass({ isActive }: { isActive: boolean }) {
  return `block rounded-md px-3 py-2 text-sm font-medium ${
    isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
  }`;
}

function ServerBanner() {
  const { down } = useServerStatus();
  if (!down) return null;
  return (
    <div role="alert" className="border-b border-red-200 bg-red-50">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-red-800">
        <span>⚠ Can't reach server. Is the backend running on port 8080?</span>
        <button
          type="button"
          onClick={() => serverStatus.retry()}
          className="rounded-md bg-red-600 px-3 py-1 text-xs font-semibold text-white hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    </div>
  );
}

export function Layout() {
  const [open, setOpen] = useState(false);
  const location = useLocation();

  // Close the phone menu after navigating.
  useEffect(() => setOpen(false), [location.pathname]);

  return (
    <MuneemChatProvider>
    <div className="min-h-screen overflow-x-clip bg-slate-50 text-slate-900">
      <header className="anim-slide-down sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <NavLink to="/" className="flex items-center gap-2 font-semibold text-slate-900">
            <span className="flex h-7 w-7 items-center justify-center rounded-md bg-indigo-600 text-sm text-white">₹</span>
            Expense Manager
          </NavLink>

          <nav className="hidden gap-1 sm:flex" aria-label="Main">
            {LINKS.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={linkClass}>
                {l.label}
              </NavLink>
            ))}
          </nav>

          <button
            type="button"
            className="rounded-md p-2 text-slate-600 hover:bg-slate-100 sm:hidden"
            aria-label={open ? 'Close menu' : 'Open menu'}
            aria-expanded={open}
            aria-controls="mobile-nav"
            onClick={() => setOpen((o) => !o)}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
            </svg>
          </button>
        </div>

        {open && (
          <nav id="mobile-nav" className="border-t border-slate-200 px-4 py-2 sm:hidden" aria-label="Main">
            {LINKS.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={linkClass}>
                {l.label}
              </NavLink>
            ))}
          </nav>
        )}
      </header>

      <ServerBanner />

      <main className="mx-auto max-w-6xl px-4 pb-24 pt-6">
        <div key={location.pathname} className="anim-fade-in">
          <Outlet />
        </div>
      </main>

      <MuneemWidget />
    </div>
    </MuneemChatProvider>
  );
}
