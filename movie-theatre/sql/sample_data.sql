BEGIN;
INSERT INTO movies(title,genre,language,duration_minutes,release_date) VALUES
('Sky Trails','Adventure','English',110,CURRENT_DATE-120),
('Evening Train','Drama','Tamil',125,CURRENT_DATE-120),
('Ocean Lights','Animation','Tamil',95,CURRENT_DATE-120);
INSERT INTO screens(name,capacity) VALUES ('Screen A',30),('Screen B',20);
INSERT INTO customers(name,contact) VALUES ('Demo Customer','demo@example.test');
INSERT INTO shows(movie_id,screen_id,starts_at,ticket_price)
SELECT m.id,sc.id,date_trunc('month',CURRENT_DATE)+make_interval(months => g.n)+interval '10 days 18 hours',150+20*g.n
FROM movies m CROSS JOIN generate_series(-2,0) AS g(n)
JOIN screens sc ON sc.name=CASE WHEN m.title='Evening Train' THEN 'Screen B' ELSE 'Screen A' END
WHERE m.title<>'Ocean Lights';
INSERT INTO shows(movie_id,screen_id,starts_at,ticket_price)
SELECT m.id,sc.id, CURRENT_DATE+interval '2 days 15 hours',200
FROM movies m CROSS JOIN screens sc WHERE m.title='Ocean Lights' AND sc.name='Screen A';
WITH added AS (
 INSERT INTO bookings(show_id,customer_id,tickets,booked_at)
 SELECT s.id,c.id,4+CAST(s.id%7 AS integer),s.starts_at-interval '3 days'
 FROM shows s CROSS JOIN customers c WHERE s.starts_at<CURRENT_DATE AND c.contact='demo@example.test'
 RETURNING *
)
INSERT INTO payments(booking_id,paid)
SELECT a.id,a.tickets*s.ticket_price FROM added a JOIN shows s ON s.id=a.show_id;
COMMIT;
