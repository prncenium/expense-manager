-- Muneem assistant (docs/ASSISTANT-SPEC.md §6): only notes are stored; chat history stays in the browser.
CREATE TABLE assistant_notes (
  id          BIGSERIAL PRIMARY KEY,
  note        VARCHAR(200) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
