-- grade: set
-- Customers who have a 'pending' order but have never had a 'delivered' order.
-- EXCEPT removes from the first set every customer_id that appears in the second.
-- This is cleaner than NOT EXISTS / NOT IN for two separate membership tests,
-- and teaches the key rule: EXCEPT deduplicates both sides automatically.
SELECT customer_id
FROM orders
WHERE status = 'pending'

EXCEPT

SELECT customer_id
FROM orders
WHERE status = 'delivered';
