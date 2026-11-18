import { Fragment, useEffect, useRef, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Button, Spinner, inputClass } from '../ui';
import { useMuneemChat } from '../../lib/muneemChat';
import type { ChatItem } from '../../lib/muneemSession';

export const MESSAGE_MAX = 1000;
export const STARTERS = [
  'Where did I spend most this month?',
  'How can I save money?',
  'This month vs last month',
  'Any unusual expenses?',
];
const PROVIDER_NAMES: Record<string, string> = { GEMINI: 'Gemini', GROQ: 'Groq' };

/** **bold** inside a line. Plain text otherwise: replies are never rendered as HTML. */
function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') && part.length > 4 ? (
      <strong key={i}>{part.slice(2, -2)}</strong>
    ) : (
      <Fragment key={i}>{part}</Fragment>
    ),
  );
}

/** Light markdown: paragraphs, "- " / "* " / "1. " list lines and **bold**. */
function ReplyText({ text }: { text: string }) {
  const lines = text.split('\n');
  return (
    <div className="space-y-1">
      {lines.map((line, i) => {
        const bullet = /^\s*(?:[-*•]|\d+[.)])\s+(.*)$/.exec(line);
        if (bullet) {
          return (
            <div key={i} className="flex gap-2 pl-1">
              <span aria-hidden="true" className="text-slate-400">•</span>
              <span>{inline(bullet[1] ?? '')}</span>
            </div>
          );
        }
        return line.trim() === '' ? <div key={i} className="h-1" /> : <p key={i}>{inline(line)}</p>;
      })}
    </div>
  );
}

/** Muneem's logo: "मु" on amber. */
export function MuneemAvatar({ size = 'sm' }: { size?: 'sm' | 'lg' }) {
  const s = size === 'lg' ? 'h-14 w-14 text-2xl' : 'h-8 w-8 text-sm';
  return (
    <span
      aria-hidden="true"
      className={`flex shrink-0 items-center justify-center rounded-full bg-amber-100 font-semibold text-amber-800 ${s}`}
    >
      मु
    </span>
  );
}

function Bubble({ item, onRetry }: { item: ChatItem; onRetry?: () => void }) {
  const { tts, speakingId, toggleSpeak } = useMuneemChat();
  const talking = speakingId === item.id;
  if (item.role === 'user') {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-sm bg-indigo-600 px-4 py-2 text-sm text-white">
          {item.content}
        </div>
      </div>
    );
  }
  return (
    <div className="flex items-start gap-2">
      <MuneemAvatar />
      <div className="min-w-0 max-w-[85%]">
        <div
          className={`break-words rounded-2xl rounded-tl-sm px-4 py-2 text-sm ${
            item.error ? 'border border-red-200 bg-red-50 text-red-800' : 'bg-slate-100 text-slate-800'
          }`}
          role={item.error ? 'alert' : undefined}
        >
          {item.error ? item.content : <ReplyText text={item.content} />}
        </div>
        {item.error && onRetry && (
          <Button variant="danger" size="sm" className="mt-1" onClick={onRetry}>
            Try again
          </Button>
        )}
        {!item.error && (
          <div className="mt-0.5 flex items-center gap-2 pl-1 text-[11px] text-slate-400">
            {tts.supported && (
              <button
                type="button"
                onClick={() => toggleSpeak(item)}
                aria-pressed={talking}
                aria-label={talking ? 'Stop reading aloud' : 'Read aloud'}
                title={talking ? 'Stop' : 'Listen'}
                className={`rounded px-1 text-sm leading-none hover:bg-slate-100 ${talking ? 'text-indigo-600' : 'text-slate-500'}`}
              >
                {talking ? '■' : '🔊'}
              </button>
            )}
            {item.provider && <span>via {PROVIDER_NAMES[item.provider] ?? item.provider}</span>}
          </div>
        )}
      </div>
    </div>
  );
}

function Thinking() {
  return (
    <div className="flex items-start gap-2" role="status" aria-label="Muneem is thinking">
      <MuneemAvatar />
      <div className="flex items-center gap-2 rounded-2xl rounded-tl-sm bg-slate-100 px-4 py-2 text-sm text-slate-500">
        <Spinner className="h-3 w-3" />
        Muneem is thinking…
      </div>
    </div>
  );
}

/** Off-switch notice when the server has no AI key. */
export function MuneemOffNotice() {
  const { status, enabled } = useMuneemChat();
  if (!status.data || enabled) return null;
  return (
    <div role="alert" className="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      Muneem is off: the server has no AI key. Add <code>GEMINI_API_KEY</code> or <code>GROQ_API_KEY</code> to the
      backend (see README) and restart it.
    </div>
  );
}

/** The scrolling list of bubbles, with a welcome + starter questions when empty. */
export function ChatMessages({ className = '', compact = false }: { className?: string; compact?: boolean }) {
  const { items, sending, enabled, send, retry } = useMuneemChat();
  const scroller = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [items, sending]);

  return (
    <div ref={scroller} className={`space-y-4 overflow-y-auto pr-1 ${className}`} aria-live="polite" aria-label="Chat with Muneem">
      {items.length === 0 ? (
        <div className="flex h-full flex-col items-center justify-center gap-4 px-2 text-center">
          <MuneemAvatar size="lg" />
          <div>
            <p className="font-medium text-slate-900">Namaste! I'm Muneem, your accountant.</p>
            <p className="mt-1 text-sm text-slate-500">
              {compact
                ? 'Ask about your spending in English, Hindi or Hinglish.'
                : 'Ask me where your money went, compare months, or how to save. English, Hindi or Hinglish.'}
            </p>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            {STARTERS.map((s) => (
              <button
                key={s}
                type="button"
                disabled={!enabled}
                onClick={() => send(s)}
                className={`rounded-full border border-indigo-200 bg-white text-indigo-700 hover:bg-indigo-50 disabled:cursor-not-allowed disabled:opacity-50 ${
                  compact ? 'px-3 py-1 text-xs' : 'px-3 py-1.5 text-sm'
                }`}
              >
                {s}
              </button>
            ))}
          </div>
        </div>
      ) : (
        items.map((m, i) => <Bubble key={m.id} item={m} onRetry={i === items.length - 1 && !sending ? retry : undefined} />)
      )}
      {sending && <Thinking />}
    </div>
  );
}

export function Suggestions({ className = '' }: { className?: string }) {
  const { suggestions, send } = useMuneemChat();
  if (suggestions.length === 0) return null;
  return (
    <div className={`flex flex-wrap gap-2 ${className}`} aria-label="Suggested questions">
      {suggestions.map((s) => (
        <button
          key={s}
          type="button"
          onClick={() => send(s)}
          className="rounded-full border border-indigo-200 bg-white px-3 py-1 text-xs text-indigo-700 hover:bg-indigo-50"
        >
          {s}
        </button>
      ))}
    </div>
  );
}

function MicIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="9" y="2" width="6" height="12" rx="3" />
      <path d="M5 10a7 7 0 0 0 14 0M12 17v5M8 22h8" />
    </svg>
  );
}

/** Text box + mic + send. `rows` 1 for the floating widget, 2 on the page. */
export function Composer({ inputId, rows = 2 }: { inputId: string; rows?: number }) {
  const { draft, setDraft, send, sending, enabled, stt, prefs, toggleMic, inputRef } = useMuneemChat();

  function submit(ev: FormEvent) {
    ev.preventDefault();
    send(draft);
  }

  function onKeyDown(ev: KeyboardEvent<HTMLTextAreaElement>) {
    if (ev.key === 'Enter' && !ev.shiftKey && !ev.nativeEvent.isComposing) {
      ev.preventDefault();
      send(draft);
    }
  }

  return (
    <>
      <form onSubmit={submit} className="flex items-end gap-2">
        <label htmlFor={inputId} className="sr-only">
          Message to Muneem
        </label>
        <textarea
          id={inputId}
          ref={inputRef}
          rows={rows}
          value={stt.listening ? stt.transcript : draft}
          maxLength={MESSAGE_MAX}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          disabled={!enabled}
          readOnly={stt.listening}
          placeholder={
            !enabled
              ? 'Muneem is off'
              : stt.listening
                ? prefs.lang === 'hi-IN'
                  ? 'बोलिए… सुन रहा हूँ'
                  : 'Listening… speak now'
                : rows > 1
                  ? 'Ask Muneem… (Enter to send, Shift+Enter for a new line)'
                  : 'Ask Muneem…'
          }
          className={`${inputClass} resize-none`}
        />
        {stt.supported && (
          <button
            type="button"
            onClick={toggleMic}
            disabled={!enabled || sending}
            aria-pressed={stt.listening}
            aria-label={stt.listening ? 'Stop listening' : 'Speak to Muneem'}
            title={stt.listening ? 'Stop listening' : 'Speak to Muneem'}
            className={`relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${
              stt.listening ? 'bg-red-600 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50'
            }`}
          >
            {stt.listening && <span aria-hidden="true" className="absolute inset-0 animate-ping rounded-full bg-red-400 opacity-60" />}
            <span className="relative">
              <MicIcon />
            </span>
          </button>
        )}
        <Button type="submit" busy={sending} disabled={!enabled || !draft.trim() || stt.listening} className="shrink-0">
          Send
        </Button>
      </form>
      {stt.error && (
        <p role="alert" className="mt-1 text-xs text-red-600">
          {stt.error}
        </p>
      )}
    </>
  );
}
