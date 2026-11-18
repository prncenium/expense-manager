import { useState } from 'react';
import { api, errorMessage, NetworkError } from '../api/client';
import { useToast } from '../components/Toasts';
import { Button, Card, ErrorBox, PageHeader, SkeletonRows, Spinner } from '../components/ui';
import { ChatMessages, Composer, MESSAGE_MAX, MuneemOffNotice, Suggestions } from '../components/muneem/ChatParts';
import { useMuneemChat } from '../lib/muneemChat';
import type { VoicePrefs } from '../lib/muneemSession';
import { shortDate } from '../lib/format';
import { useAsync } from '../lib/useAsync';
import type { AssistantNote } from '../types';

function NotesPanel({
  notes,
  loading,
  error,
  reload,
}: {
  notes: AssistantNote[];
  loading: boolean;
  error: string | null;
  reload: () => void;
}) {
  const toast = useToast();
  const [busyId, setBusyId] = useState<number | 'all' | null>(null);

  async function forget(id: number | 'all') {
    if (id === 'all' && !window.confirm('Forget everything Muneem remembers about you?')) return;
    setBusyId(id);
    try {
      if (id === 'all') await api.deleteAllNotes();
      else await api.deleteNote(id);
      toast.success(id === 'all' ? 'Muneem forgot all notes' : 'Note forgotten');
      reload();
    } catch (e) {
      if (!(e instanceof NetworkError)) toast.error(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between gap-2">
        <h2 className="text-base font-semibold text-slate-900">What Muneem remembers</h2>
        {notes.length > 0 && (
          <Button variant="ghost" size="sm" busy={busyId === 'all'} onClick={() => forget('all')}>
            Forget all
          </Button>
        )}
      </div>
      <p className="mb-3 text-xs text-slate-500">
        Things you told Muneem, used in every chat. Up to 30; the oldest goes first.
      </p>
      {error ? (
        <ErrorBox message={error} onRetry={reload} />
      ) : loading && notes.length === 0 ? (
        <SkeletonRows rows={2} />
      ) : notes.length === 0 ? (
        <p className="text-sm text-slate-500">Nothing yet. Answer Muneem's questions and he'll remember.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {notes.map((n) => (
            <li key={n.id} className="flex items-start justify-between gap-2 py-2">
              <div className="min-w-0">
                <p className="break-words text-sm text-slate-800">{n.note}</p>
                <p className="text-[11px] text-slate-400">{shortDate(n.createdAt.slice(0, 10))}</p>
              </div>
              <button
                type="button"
                onClick={() => forget(n.id)}
                disabled={busyId !== null}
                aria-label={`Forget: ${n.note}`}
                className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-red-600 disabled:opacity-50"
              >
                {busyId === n.id ? (
                  <Spinner className="h-3.5 w-3.5" />
                ) : (
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                    <path d="M6 6l12 12M18 6L6 18" />
                  </svg>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Language picker and a stop button while Muneem is talking. */
function VoiceControls({
  prefs,
  onChange,
  speaking,
  onStop,
}: {
  prefs: VoicePrefs;
  onChange: (p: VoicePrefs) => void;
  speaking: boolean;
  onStop: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      <label className="flex items-center gap-1.5 text-slate-600">
        <span className="sr-only sm:not-sr-only">Language</span>
        <select
          value={prefs.lang}
          onChange={(e) => onChange({ ...prefs, lang: e.target.value === 'hi-IN' ? 'hi-IN' : 'en-IN' })}
          className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
          aria-label="Voice language"
        >
          <option value="en-IN">English (India)</option>
          <option value="hi-IN">हिन्दी</option>
        </select>
      </label>
      {speaking && (
        <Button variant="secondary" size="sm" onClick={onStop}>
          ■ Stop speaking
        </Button>
      )}
    </div>
  );
}

export function MuneemPage() {
  const chat = useMuneemChat();
  const notes = useAsync(() => api.notes(), [chat.notesVersion]);
  const { stt, tts } = chat;

  return (
    <div>
      <PageHeader title="Muneem · your accountant">
        <div className="flex flex-wrap items-center gap-3">
          <VoiceControls
            prefs={chat.prefs}
            onChange={chat.changePrefs}
            speaking={tts.speaking}
            onStop={tts.stop}
          />
          {chat.items.length > 0 && (
            <Button variant="secondary" size="sm" onClick={chat.newChat} disabled={chat.sending}>
              New chat
            </Button>
          )}
        </div>
      </PageHeader>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="flex flex-col lg:col-span-2">
          {chat.status.error ? <ErrorBox message={chat.status.error} onRetry={chat.status.reload} /> : <MuneemOffNotice />}

          <ChatMessages className="h-[60vh] min-h-[320px]" />

          <Suggestions className="mt-3" />

          <div className="mt-3 border-t border-slate-100 pt-3">
            <Composer inputId="muneem-input" />
          </div>
          <div className="mt-1 flex justify-between gap-2 text-[11px] text-slate-400">
            <span>
              Chats clear when you close the tab. Muneem remembers only the notes.
              {!stt.supported && ' Voice works in Chrome and Edge.'}
            </span>
            {chat.draft.length > MESSAGE_MAX - 200 && (
              <span>
                {chat.draft.length}/{MESSAGE_MAX}
              </span>
            )}
          </div>
        </Card>

        <div className="space-y-4">
          <NotesPanel notes={notes.data ?? []} loading={notes.loading} error={notes.error} reload={notes.reload} />
          <Card>
            <h2 className="mb-1 text-base font-semibold text-slate-900">Good to know</h2>
            <ul className="list-disc space-y-1 pl-5 text-xs text-slate-500">
              <li>Muneem only reads your expenses; he can't add, change or delete them.</li>
              <li>Numbers come straight from your data, not from the AI's guesses.</li>
              <li>Uses free AI plans (Gemini, then Groq). Only summaries and a few rows are sent.</li>
              <li>Tap the mic, speak, then pause: Muneem answers short. Tap 🔊 under a reply to hear it.</li>
              <li>The "Ask Muneem" button at the bottom right of every page opens this same chat.</li>
            </ul>
          </Card>
        </div>
      </div>
    </div>
  );
}
