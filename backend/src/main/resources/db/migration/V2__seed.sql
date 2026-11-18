INSERT INTO categories (name) VALUES
  ('Food'),
  ('Groceries'),
  ('Transport'),
  ('Shopping'),
  ('Entertainment'),
  ('Bills & Utilities'),
  ('Travel'),
  ('Health'),
  ('Other');

INSERT INTO vendor_rules (keyword, category_id)
SELECT r.keyword, c.id
FROM (VALUES
  ('swiggy',       'Food'),
  ('zomato',       'Food'),
  ('blinkit',      'Groceries'),
  ('zepto',        'Groceries'),
  ('bigbasket',    'Groceries'),
  ('uber',         'Transport'),
  ('ola',          'Transport'),
  ('rapido',       'Transport'),
  ('amazon',       'Shopping'),
  ('flipkart',     'Shopping'),
  ('myntra',       'Shopping'),
  ('netflix',      'Entertainment'),
  ('amazon prime', 'Entertainment'),
  ('bookmyshow',   'Entertainment'),
  ('airtel',       'Bills & Utilities'),
  ('jio',          'Bills & Utilities'),
  ('electricity',  'Bills & Utilities'),
  ('irctc',        'Travel'),
  ('indigo',       'Travel'),
  ('makemytrip',   'Travel'),
  ('apollo',       'Health'),
  ('pharmeasy',    'Health')
) AS r(keyword, category)
JOIN categories c ON c.name = r.category;
