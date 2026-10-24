-- Chạy sau khi đã nạp schema-mysql.sql và seed-demo-data.sql.
-- Truy vấn đầu trả về số lượng từng bảng; truy vấn thứ hai phải trả về 0 dòng.
SELECT table_name, row_count FROM (
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'oauth_identities' AS table_name, COUNT(*) AS row_count FROM oauth_identities
UNION ALL
SELECT 'customer_profiles' AS table_name, COUNT(*) AS row_count FROM customer_profiles
UNION ALL
SELECT 'provider_profiles' AS table_name, COUNT(*) AS row_count FROM provider_profiles
UNION ALL
SELECT 'skills' AS table_name, COUNT(*) AS row_count FROM skills
UNION ALL
SELECT 'provider_skills' AS table_name, COUNT(*) AS row_count FROM provider_skills
UNION ALL
SELECT 'services' AS table_name, COUNT(*) AS row_count FROM services
UNION ALL
SELECT 'service_skills' AS table_name, COUNT(*) AS row_count FROM service_skills
UNION ALL
SELECT 'service_requests' AS table_name, COUNT(*) AS row_count FROM service_requests
UNION ALL
SELECT 'service_request_skills' AS table_name, COUNT(*) AS row_count FROM service_request_skills
UNION ALL
SELECT 'applies' AS table_name, COUNT(*) AS row_count FROM applies
UNION ALL
SELECT 'carts' AS table_name, COUNT(*) AS row_count FROM carts
UNION ALL
SELECT 'cart_items' AS table_name, COUNT(*) AS row_count FROM cart_items
UNION ALL
SELECT 'bookings' AS table_name, COUNT(*) AS row_count FROM bookings
UNION ALL
SELECT 'rental_orders' AS table_name, COUNT(*) AS row_count FROM rental_orders
UNION ALL
SELECT 'rental_order_items' AS table_name, COUNT(*) AS row_count FROM rental_order_items
UNION ALL
SELECT 'rental_order_status_history' AS table_name, COUNT(*) AS row_count FROM rental_order_status_history
UNION ALL
SELECT 'payments' AS table_name, COUNT(*) AS row_count FROM payments
UNION ALL
SELECT 'payment_attempts' AS table_name, COUNT(*) AS row_count FROM payment_attempts
UNION ALL
SELECT 'refunds' AS table_name, COUNT(*) AS row_count FROM refunds
UNION ALL
SELECT 'reviews' AS table_name, COUNT(*) AS row_count FROM reviews
UNION ALL
SELECT 'complaints' AS table_name, COUNT(*) AS row_count FROM complaints
UNION ALL
SELECT 'conversations' AS table_name, COUNT(*) AS row_count FROM conversations
UNION ALL
SELECT 'messages' AS table_name, COUNT(*) AS row_count FROM messages
) AS row_counts ORDER BY table_name;

SELECT table_name, row_count FROM (
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'oauth_identities' AS table_name, COUNT(*) AS row_count FROM oauth_identities
UNION ALL
SELECT 'customer_profiles' AS table_name, COUNT(*) AS row_count FROM customer_profiles
UNION ALL
SELECT 'provider_profiles' AS table_name, COUNT(*) AS row_count FROM provider_profiles
UNION ALL
SELECT 'skills' AS table_name, COUNT(*) AS row_count FROM skills
UNION ALL
SELECT 'provider_skills' AS table_name, COUNT(*) AS row_count FROM provider_skills
UNION ALL
SELECT 'services' AS table_name, COUNT(*) AS row_count FROM services
UNION ALL
SELECT 'service_skills' AS table_name, COUNT(*) AS row_count FROM service_skills
UNION ALL
SELECT 'service_requests' AS table_name, COUNT(*) AS row_count FROM service_requests
UNION ALL
SELECT 'service_request_skills' AS table_name, COUNT(*) AS row_count FROM service_request_skills
UNION ALL
SELECT 'applies' AS table_name, COUNT(*) AS row_count FROM applies
UNION ALL
SELECT 'carts' AS table_name, COUNT(*) AS row_count FROM carts
UNION ALL
SELECT 'cart_items' AS table_name, COUNT(*) AS row_count FROM cart_items
UNION ALL
SELECT 'bookings' AS table_name, COUNT(*) AS row_count FROM bookings
UNION ALL
SELECT 'rental_orders' AS table_name, COUNT(*) AS row_count FROM rental_orders
UNION ALL
SELECT 'rental_order_items' AS table_name, COUNT(*) AS row_count FROM rental_order_items
UNION ALL
SELECT 'rental_order_status_history' AS table_name, COUNT(*) AS row_count FROM rental_order_status_history
UNION ALL
SELECT 'payments' AS table_name, COUNT(*) AS row_count FROM payments
UNION ALL
SELECT 'payment_attempts' AS table_name, COUNT(*) AS row_count FROM payment_attempts
UNION ALL
SELECT 'refunds' AS table_name, COUNT(*) AS row_count FROM refunds
UNION ALL
SELECT 'reviews' AS table_name, COUNT(*) AS row_count FROM reviews
UNION ALL
SELECT 'complaints' AS table_name, COUNT(*) AS row_count FROM complaints
UNION ALL
SELECT 'conversations' AS table_name, COUNT(*) AS row_count FROM conversations
UNION ALL
SELECT 'messages' AS table_name, COUNT(*) AS row_count FROM messages
) AS row_counts WHERE row_count < 50 ORDER BY table_name;
