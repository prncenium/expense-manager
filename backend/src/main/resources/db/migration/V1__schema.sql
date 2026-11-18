CREATE TABLE categories (
  id    SMALLSERIAL PRIMARY KEY,
  name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE vendor_rules (
  id           BIGSERIAL PRIMARY KEY,
  keyword      VARCHAR(100) NOT NULL,          -- stored lower-case, trimmed
  category_id  SMALLINT NOT NULL REFERENCES categories(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_vendor_rules_keyword UNIQUE (keyword)
);

CREATE TABLE expenses (
  id           BIGSERIAL PRIMARY KEY,
  expense_date DATE NOT NULL,
  amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
  vendor       VARCHAR(150) NOT NULL,
  description  VARCHAR(500),
  category_id  SMALLINT NOT NULL REFERENCES categories(id),
  source       VARCHAR(10) NOT NULL DEFAULT 'MANUAL',   -- MANUAL | CSV
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_expenses_category ON expenses(category_id);
CREATE INDEX idx_expenses_date     ON expenses(expense_date);
-- fast duplicate lookup (date + amount + normalized vendor)
CREATE INDEX idx_expenses_dup ON expenses(expense_date, amount, lower(trim(vendor)));
