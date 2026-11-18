-- Show booking totals, seats and occupancy.
SELECT *,capacity-booked AS available,round(100.0*booked/capacity,2) AS occupancy_pct
FROM show_summary ORDER BY starts_at,id;
-- Individual bookings and payment history.
SELECT b.id,b.show_id,c.name,b.tickets,b.booked_at,b.cancelled_at,p.paid,p.refunded
FROM bookings b JOIN customers c ON c.id=b.customer_id JOIN payments p ON p.booking_id=b.id ORDER BY b.id;
-- Net revenue by movie.
SELECT m.id,m.title,COALESCE(SUM(v.revenue),0) AS net_revenue
FROM movies m LEFT JOIN shows s ON s.movie_id=m.id LEFT JOIN show_summary v ON v.id=s.id
GROUP BY m.id,m.title ORDER BY net_revenue DESC,m.id;
