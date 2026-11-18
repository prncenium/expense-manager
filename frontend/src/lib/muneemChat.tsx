import { createContext, useContext, useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { api, ApiError, errorMessage, NetworkError } from '../api/client';
import { historyFor, loadChat, loadPrefs, saveChat, savePrefs, type ChatItem, type VoicePrefs } from './muneemSession';
import { useAsync, type AsyncState } from './useAsync';
import { useSpeechRecognition, type SpeechRecognitionApi } from './useSpeechRecognition';
import { useSpeechSynthesis, type SpeechSynthesisApi } from './useSpeechSynthesis';
import type { AssistantStatus } from '../types';

export interface MuneemChat {
  status: AsyncState<AssistantStatus>;
  enabled: boolean;
  items: ChatItem[];
  draft: string;
  setDraft: (d: string) => void;
  sending: boolean;
  suggestions: string[];
  prefs: VoicePrefs;
  changePrefs: (p: VoicePrefs) => void;
  stt: SpeechRecognitionApi;
  tts: SpeechSynthesisApi;
  inputRef: RefObject<HTMLTextAreaElement>;
  send: (text: string) => void;
  retry: () => void;
  toggleMic: () => void;
  newChat: () => void;
  /** bumps when Muneem saved a note, so the notes list can reload */
  notesVersion: number;
}

const Ctx = createContext<MuneemChat | null>(null);

/**
 * One chat for the whole app: the floating widget and the Muneem page share it,
 * so a question asked on one shows on the other and a reply in flight survives navigation.
 */
export function MuneemChatProvider({ children }: { children: ReactNode }) {
  const status = useAsync(() => api.assistantStatus(), []);
  const [items, setItems] = useState<ChatItem[]>(loadChat);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [notesVersion, setNotesVersion] = useState(0);
  const nextId = useRef(items.reduce((max, m) => Math.max(max, m.id), 0) + 1);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const [prefs, setPrefs] = useState<VoicePrefs>(loadPrefs);
  const stt = useSpeechRecognition();
  const tts = useSpeechSynthesis();

  useEffect(() => saveChat(items), [items]);

  const enabled = status.data?.enabled ?? false;

  function changePrefs(p: VoicePrefs) {
    setPrefs(p);
    savePrefs(p);
    if (!p.speak) tts.stop();
  }

  async function sendWith(text: string, base: ChatItem[] = items, voice = false) {
    const message = text.trim();
    if (!message || sending || !enabled) return;
    tts.stop();
    const history = historyFor(base);
    setItems([...base, { id: nextId.current++, role: 'user', content: message, voice }]);
    setDraft('');
    setSending(true);
    try {
      const r = await api.chat({ message, history, voice, lang: prefs.lang });
      setItems((cur) => [
        ...cur,
        { id: nextId.current++, role: 'assistant', content: r.reply, provider: r.provider, suggestions: r.suggestions },
      ]);
      if (r.toolsUsed.includes('save_note')) setNotesVersion((v) => v + 1);
      if (voice && prefs.speak) tts.speak(r.reply, prefs.lang);
    } catch (e) {
      const content =
        e instanceof NetworkError
          ? "Can't reach the server. Check that the backend is running."
          : e instanceof ApiError && (e.status === 429 || e.status === 503)
            ? e.message
            : errorMessage(e);
      setItems((cur) => [...cur, { id: nextId.current++, role: 'assistant', content, error: true }]);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }

  // the recognizer calls back after a pause; always use the latest send (current chat + prefs)
  const sendRef = useRef(sendWith);
  sendRef.current = sendWith;

  /** Re-asks the question before a failed reply (the error bubble and the question are replaced). */
  function retry() {
    const lastError = items.length - 1;
    const question = items[lastError - 1];
    if (!items[lastError]?.error || question?.role !== 'user') return;
    void sendWith(question.content, items.slice(0, lastError - 1), question.voice ?? false);
  }

  function toggleMic() {
    if (stt.listening) {
      stt.stop();
      return;
    }
    tts.stop();
    stt.start(prefs.lang, (heard) => void sendRef.current(heard, undefined, true));
  }

  function newChat() {
    tts.stop();
    setItems([]);
  }

  const last = items[items.length - 1];
  const suggestions = !sending && last?.role === 'assistant' && !last.error ? (last.suggestions ?? []) : [];

  const value: MuneemChat = {
    status,
    enabled,
    items,
    draft,
    setDraft,
    sending,
    suggestions,
    prefs,
    changePrefs,
    stt,
    tts,
    inputRef,
    send: (text) => void sendWith(text),
    retry,
    toggleMic,
    newChat,
    notesVersion,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useMuneemChat(): MuneemChat {
  const c = useContext(Ctx);
  if (!c) throw new Error('useMuneemChat must be used inside <MuneemChatProvider>');
  return c;
}
