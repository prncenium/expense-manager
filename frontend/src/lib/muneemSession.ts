import type { ChatRole } from '../types';

/** One bubble in the Muneem chat. Errors are shown but never sent back as history. */
export interface ChatItem {
  id: number;
  role: ChatRole;
  content: string;
  provider?: string;
  suggestions?: string[];
  error?: boolean;
  /** a question asked with the mic (its reply is short and may be read aloud) */
  voice?: boolean;
}

const KEY = 'muneem.chat';
/** Browser-side cap (ASSISTANT-SPEC §3.12); the server takes at most 20 of these as history. */
export const KEEP_MESSAGES = 40;
export const HISTORY_MESSAGES = 20;

/** Chat history lives only in this tab (sessionStorage): closing the tab clears it. */
export function loadChat(): ChatItem[] {
  try {
    const raw = sessionStorage.getItem(KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? (parsed as ChatItem[]).slice(-KEEP_MESSAGES) : [];
  } catch {
    return [];
  }
}

export function saveChat(items: ChatItem[]): void {
  try {
    if (items.length === 0) sessionStorage.removeItem(KEY);
    else sessionStorage.setItem(KEY, JSON.stringify(items.slice(-KEEP_MESSAGES)));
  } catch {
    // storage full or blocked: the chat still works, it just won't survive a reload
  }
}

/** The last messages to send as context (errors left out). */
export function historyFor(items: ChatItem[]): { role: ChatRole; content: string }[] {
  return items
    .filter((m) => !m.error)
    .slice(-HISTORY_MESSAGES)
    .map((m) => ({ role: m.role, content: m.content }));
}

export type VoiceLang = 'en-IN' | 'hi-IN';

export interface VoicePrefs {
  lang: VoiceLang;
}

const PREFS_KEY = 'muneem.voice';

/** Per-browser convenience (localStorage); falls back to English. */
export function loadPrefs(): VoicePrefs {
  try {
    const p = JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') as Partial<VoicePrefs>;
    return { lang: p.lang === 'hi-IN' ? 'hi-IN' : 'en-IN' };
  } catch {
    return { lang: 'en-IN' };
  }
}

export function savePrefs(p: VoicePrefs): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(p));
  } catch {
    // blocked storage: the choice just isn't remembered
  }
}
