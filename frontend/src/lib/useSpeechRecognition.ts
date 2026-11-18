import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Browser speech-to-text (Web Speech API). Free and built in, but only in Chrome / Edge
 * (Chrome sends the audio to Google to transcribe, so it needs internet). Not in Firefox.
 * TypeScript's DOM lib has no types for it, so the small part we use is typed here.
 */
interface RecognitionAlternative {
  transcript: string;
}
interface RecognitionResult {
  readonly isFinal: boolean;
  readonly length: number;
  [index: number]: RecognitionAlternative;
}
interface RecognitionEvent {
  readonly resultIndex: number;
  readonly results: { readonly length: number; [index: number]: RecognitionResult };
}
interface Recognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((e: RecognitionEvent) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}
type RecognitionCtor = new () => Recognition;

function ctor(): RecognitionCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { SpeechRecognition?: RecognitionCtor; webkitSpeechRecognition?: RecognitionCtor };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

const ERRORS: Record<string, string> = {
  'not-allowed': 'Microphone permission was denied. Allow it in the browser and try again.',
  'service-not-allowed': 'Microphone permission was denied. Allow it in the browser and try again.',
  'audio-capture': 'No microphone found.',
  'no-speech': "Didn't hear anything. Tap the mic and speak.",
  network: 'Voice needs an internet connection.',
  'language-not-supported': 'This browser cannot listen in the chosen language.',
};

export interface SpeechRecognitionApi {
  supported: boolean;
  listening: boolean;
  /** Words heard so far (final + in-progress), for showing while listening. */
  transcript: string;
  error: string | null;
  /** Starts listening; `onFinal` gets the whole sentence once the user stops speaking. */
  start: (lang: string, onFinal: (text: string) => void) => void;
  /** Stops listening and still delivers what was heard. */
  stop: () => void;
}

export function useSpeechRecognition(): SpeechRecognitionApi {
  const supported = ctor() !== null;
  const [listening, setListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);
  const rec = useRef<Recognition | null>(null);

  const start = useCallback((lang: string, onFinal: (text: string) => void) => {
    const Ctor = ctor();
    if (!Ctor) return;
    rec.current?.abort();
    const r = new Ctor();
    r.lang = lang;
    r.continuous = false; // stops by itself after a pause
    r.interimResults = true;
    r.maxAlternatives = 1;
    let finalText = '';
    r.onresult = (e) => {
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const result = e.results[i];
        const text = result?.[0]?.transcript ?? '';
        if (result?.isFinal) finalText += text;
        else interim += text;
      }
      setTranscript((finalText + interim).trim());
    };
    r.onerror = (e) => {
      if (e.error !== 'aborted') setError(ERRORS[e.error] ?? 'Voice input stopped. Try again or type instead.');
    };
    r.onend = () => {
      if (rec.current === r) rec.current = null;
      setListening(false);
      setTranscript('');
      const text = finalText.trim();
      if (text) onFinal(text);
    };
    rec.current = r;
    setError(null);
    setTranscript('');
    setListening(true);
    try {
      r.start();
    } catch {
      rec.current = null;
      setListening(false);
      setError('Could not start the microphone. Try again.');
    }
  }, []);

  const stop = useCallback(() => rec.current?.stop(), []);

  // never keep the mic open after leaving the page
  useEffect(
    () => () => {
      const r = rec.current;
      rec.current = null;
      if (r) {
        r.onend = null;
        r.abort();
      }
    },
    [],
  );

  return { supported, listening, transcript, error, start, stop };
}
