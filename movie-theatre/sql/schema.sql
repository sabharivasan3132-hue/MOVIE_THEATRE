BEGIN;
CREATE TABLE movies(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, title text NOT NULL, genre text NOT NULL, language text NOT NULL, duration_minutes integer NOT NULL CHECK(duration_minutes>0), release_date date NOT NULL);
CREATE TABLE screens(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name text NOT NULL UNIQUE, capacity integer NOT NULL CHECK(capacity>0));
CREATE TABLE customers(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name text NOT NULL, contact text NOT NULL UNIQUE);
CREATE TABLE shows(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, movie_id bigint NOT NULL REFERENCES movies, screen_id bigint NOT NULL REFERENCES screens, starts_at timestamp NOT NULL, ticket_price numeric(10,2) NOT NULL CHECK(ticket_price>0), UNIQUE(screen_id,starts_at));
CREATE TABLE bookings(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, show_id bigint NOT NULL REFERENCES shows, customer_id bigint NOT NULL REFERENCES customers, tickets integer NOT NULL CHECK(tickets>0), booked_at timestamp NOT NULL DEFAULT LOCALTIMESTAMP, cancelled_at timestamp CHECK(cancelled_at>=booked_at));
CREATE TABLE payments(booking_id bigint PRIMARY KEY REFERENCES bookings, paid numeric(14,2) NOT NULL CHECK(paid>0), refunded numeric(14,2) NOT NULL DEFAULT 0 CHECK(refunded>=0 AND refunded<=paid));
CREATE INDEX bookings_show_idx ON bookings(show_id);
CREATE VIEW show_summary AS
SELECT s.id, m.title, sc.name AS screen, s.starts_at, sc.capacity,
 COALESCE(SUM(b.tickets) FILTER (WHERE b.cancelled_at IS NULL),0) AS booked,
 COALESCE(SUM(p.paid-p.refunded),0) AS revenue
FROM shows s JOIN movies m ON m.id=s.movie_id JOIN screens sc ON sc.id=s.screen_id
LEFT JOIN bookings b ON b.show_id=s.id LEFT JOIN payments p ON p.booking_id=b.id
GROUP BY s.id,m.title,sc.name,sc.capacity;
COMMIT;
