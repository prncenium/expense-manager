import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useMuneemChat } from '../../lib/muneemChat';
import { ErrorBox } from '../ui';
import { ChatMessages, Composer, MuneemAvatar, MuneemOffNotice, Suggestions } from './ChatParts';

function IconButton({ label, onClick, children }: { label: string; onClick?: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
    >
      {children}
    </button>
  );
}

const svg = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true };

/**
 * Floating "Ask Muneem" button (bottom right) on every page except /muneem itself.
 * Opens a chat panel that shares its conversation with the Muneem page.
 */
export function MuneemWidget() {
  const location = useLocation();
  const chat = useMuneemChat();
  const [open, setOpen] = useState(false);
  const onMuneemPage = location.pathname.startsWith('/muneem');

  // focus the box on open; Esc closes
  useEffect(() => {
    if (!open) return;
    chat.inputRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (onMuneemPage) setOpen(false);
  }, [onMuneemPage]);

  if (onMuneemPage) return null;

  return (
    <>
      {open && (
        <div
          id="muneem-widget"
          role="dialog"
          aria-label="Muneem, your AI accountant"
          className="fixed inset-0 z-40 flex flex-col bg-white sm:inset-auto sm:bottom-24 sm:right-6 sm:h-[min(620px,calc(100vh-8rem))] sm:w-[400px] sm:rounded-2xl sm:border sm:border-slate-200 sm:shadow-2xl"
        >
          <header className="flex items-center gap-3 border-b border-slate-100 px-4 py-3">
            <MuneemAvatar />
            <div className="min-w-0 flex-1">
              <p className="font-semibold leading-tight text-slate-900">Muneem</p>
              <p className="flex items-center gap-1.5 text-xs text-slate-500">
                <span
                  aria-hidden="true"
                  className={`h-1.5 w-1.5 rounded-full ${chat.enabled ? 'bg-emerald-500' : 'bg-slate-300'}`}
                />
                {chat.enabled ? 'Your AI accountant' : chat.status.loading ? 'Connecting…' : 'Offline'}
              </p>
            </div>
            {chat.tts.speaking && (
              <IconButton label="Stop speaking" onClick={chat.tts.stop}>
                <svg {...svg}>
                  <rect x="6" y="6" width="12" height="12" rx="1" />
                </svg>
              </IconButton>
            )}
            {chat.items.length > 0 && (
              <IconButton label="New chat" onClick={chat.sending ? undefined : chat.newChat}>
                <svg {...svg}>
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </IconButton>
            )}
            <Link
              to="/muneem"
              aria-label="Open full Muneem page"
              title="Open full page"
              className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
            >
              <svg {...svg}>
                <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
              </svg>
            </Link>
            <IconButton label="Close" onClick={() => setOpen(false)}>
              <svg {...svg}>
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </IconButton>
          </header>

          <div className="flex min-h-0 flex-1 flex-col px-4 pt-3">
            {chat.status.error ? <ErrorBox message={chat.status.error} onRetry={chat.status.reload} /> : <MuneemOffNotice />}
            <ChatMessages compact className="min-h-0 flex-1 pb-2" />
          </div>

          <div className="border-t border-slate-100 px-4 pb-3 pt-2">
            <Suggestions className="mb-2" />
            <Composer inputId="muneem-widget-input" rows={1} />
          </div>
        </div>
      )}

      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-controls="muneem-widget"
        aria-label={open ? 'Close Muneem' : 'Ask Muneem, your AI accountant'}
        className={`group fixed bottom-5 right-5 z-40 items-center gap-2 rounded-full bg-indigo-600 py-2 pl-2 text-white shadow-lg shadow-indigo-600/30 transition hover:bg-indigo-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 sm:bottom-6 sm:right-6 ${
          open ? 'hidden pr-2 sm:flex' : 'flex pr-4'
        }`}
      >
        {open ? (
          <span className="flex h-10 w-10 items-center justify-center">
            <svg {...svg} width={22} height={22}>
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </span>
        ) : (
          <>
            <span className="relative flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-base font-semibold text-amber-800">
              मु
              {chat.sending && (
                <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 h-3 w-3 animate-pulse rounded-full bg-emerald-400 ring-2 ring-indigo-600" />
              )}
            </span>
            <span className="text-sm font-semibold">Ask Muneem</span>
          </>
        )}
      </button>
    </>
  );
}
