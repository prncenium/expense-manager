import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Browser text-to-speech (speechSynthesis): free, built in, works offline with the system voices.
 * Text is cut into sentences because Chrome stops long utterances after ~15 s.
 */

/** Makes a reply sound right: no markdown symbols, "₹6,200" read as "6,200 rupees". */
export function speakable(text: string, lang: string): string {
  const rupees = lang === 'hi-IN' ? 'रुपये' : 'rupees';
  return text
    .replace(/\*\*/g, '')
    .replace(/^\s*(?:[-*•]|\d+[.)])\s+/gm, '')
    .replace(/₹\s?([\d,]+(?:\.\d+)?)/g, `$1 ${rupees}`)
    .replace(/×/g, lang === 'hi-IN' ? ' गुना' : ' times')
    .replace(/\s*\n+\s*/g, '. ')
    .replace(/\.\s*\./g, '.')
    .trim();
}

function sentences(text: string): string[] {
  return (text.match(/[^.!?।]+[.!?।]*/g) ?? [text]).map((s) => s.trim()).filter(Boolean);
}

function pickVoice(lang: string): SpeechSynthesisVoice | null {
  const voices = window.speechSynthesis.getVoices();
  const exact = voices.filter((v) => v.lang.replace('_', '-') === lang);
  const base = voices.filter((v) => v.lang.toLowerCase().startsWith(lang.slice(0, 2)));
  // prefer the nicer online voices (e.g. "Google हिन्दी") when present
  return exact.find((v) => !v.localService) ?? exact[0] ?? base[0] ?? null;
}

export interface SpeechSynthesisApi {
  supported: boolean;
  speaking: boolean;
  speak: (text: string, lang: string) => void;
  stop: () => void;
}

export function useSpeechSynthesis(): SpeechSynthesisApi {
  const supported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const [speaking, setSpeaking] = useState(false);
  const run = useRef(0);

  // voices load asynchronously in Chrome; asking once makes them ready for the first reply
  useEffect(() => {
    if (!supported) return;
    window.speechSynthesis.getVoices();
    const noop = () => undefined;
    window.speechSynthesis.addEventListener('voiceschanged', noop);
    return () => window.speechSynthesis.removeEventListener('voiceschanged', noop);
  }, [supported]);

  const stop = useCallback(() => {
    if (!supported) return;
    run.current++;
    window.speechSynthesis.cancel();
    setSpeaking(false);
  }, [supported]);

  const speak = useCallback(
    (text: string, lang: string) => {
      if (!supported) return;
      window.speechSynthesis.cancel();
      const id = ++run.current;
      const parts = sentences(speakable(text, lang));
      if (parts.length === 0) return;
      const voice = pickVoice(lang);
      setSpeaking(true);
      parts.forEach((part, i) => {
        const u = new SpeechSynthesisUtterance(part);
        u.lang = lang;
        if (voice) u.voice = voice;
        if (i === parts.length - 1) {
          u.onend = () => {
            if (run.current === id) setSpeaking(false);
          };
        }
        u.onerror = () => {
          if (run.current === id) setSpeaking(false);
        };
        window.speechSynthesis.speak(u);
      });
    },
    [supported],
  );

  // stop talking when the page is left
  useEffect(
    () => () => {
      if (supported) window.speechSynthesis.cancel();
    },
    [supported],
  );

  return { supported, speaking, speak, stop };
}
