-- v2 (SPEC A.2): optional nickname for rules / expenses in the Other category
ALTER TABLE vendor_rules ADD COLUMN label VARCHAR(50);
ALTER TABLE expenses     ADD COLUMN label VARCHAR(50);
CREATE INDEX idx_expenses_other_label ON expenses(category_id, label);
