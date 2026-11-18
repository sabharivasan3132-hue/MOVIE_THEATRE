package college.theatre;

import jakarta.persistence.*;
import java.io.Console;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

public class TheatreApp {
    private record Input(Console console) {
        String text(String label) {
            String value=console.readLine("%s: ",label);
            if(value==null || value.isBlank()) throw new IllegalArgumentException("Enter a value for "+label);
            return value.strip();
        }
        long id(String label) {
            long value=Long.parseLong(text(label));
            if(value<=0) throw new IllegalArgumentException(label+" must be positive");
            return value;
        }
        int count(String label) { return Math.toIntExact(id(label)); }
        BigDecimal price() {
            BigDecimal price=new BigDecimal(text("Ticket price in INR"));
            if(price.signum()<=0 || price.scale()>2) throw new IllegalArgumentException("Enter a positive price with up to two decimals.");
            return price;
        }
    }

    public static void main(String[] args) {
        Console console=System.console();
        if(console==null) { System.err.println("Run in the VS Code integrated terminal."); return; }
        char[] password=console.readPassword("PostgreSQL password for postgres: ");
        if(password==null) return;
        try(var factory=Persistence.createEntityManagerFactory("movie-theatre",
                Map.of("jakarta.persistence.jdbc.password",new String(password)))) {
            Arrays.fill(password,'\0');
            var input=new Input(console);
            while(true) {
                System.out.println("""
                    1 List movies and screens
                    2 Add movie
                    3 Add screen
                    4 Schedule show
                    5 Show availability and occupancy
                    6 Book tickets
                    7 Cancel booking and refund
                    8 Booking and payment history
                    9 Revenue by movie
                    0 Exit
                    """);
                String choice=console.readLine("Choice: ");
                if(choice==null || choice.strip().equals("0")) return;
                try(var db=factory.createEntityManager()) { menu(choice.strip(),db,input); }
                catch(RuntimeException ex) { System.err.println("Operation failed: "+ex.getMessage()); }
            }
        } finally { Arrays.fill(password,'\0'); }
    }

    private static void menu(String choice,EntityManager db,Input in) {
        switch(choice) {
            case "1" -> {
                db.createQuery("SELECT m FROM Movie m ORDER BY m.id",Movie.class).getResultList().forEach(System.out::println);
                report(db,"Screen ID | Name | Capacity","SELECT id,name,capacity FROM screens ORDER BY id");
            }
            case "2" -> add(db,"INSERT INTO movies(title,genre,language,duration_minutes,release_date) VALUES (?1,?2,?3,?4,CAST(?5 AS date))",
                    in.text("Title"),in.text("Genre"),in.text("Language"),in.count("Duration in minutes"),in.text("Release date YYYY-MM-DD"));
            case "3" -> add(db,"INSERT INTO screens(name,capacity) VALUES (?1,?2)",in.text("Screen name"),in.count("Seat capacity"));
            case "4" -> schedule(db,in);
            case "5" -> report(db,"Show ID | Movie | Screen | Start | Booked | Available | Occupancy %",
                    "SELECT id,title,screen,starts_at,booked,capacity-booked,round(100.0*booked/capacity,2) FROM show_summary ORDER BY starts_at,id");
            case "6" -> book(db,in);
            case "7" -> cancel(db,in);
            case "8" -> report(db,"Booking ID | Show ID | Customer | Tickets | Date | Status | Paid | Refund",
                    "SELECT b.id,b.show_id,c.name,b.tickets,b.booked_at,CASE WHEN b.cancelled_at IS NULL THEN 'Confirmed' ELSE 'Cancelled' END,p.paid,p.refunded FROM bookings b JOIN customers c ON c.id=b.customer_id JOIN payments p ON p.booking_id=b.id ORDER BY b.id");
            case "9" -> report(db,"Movie | Net revenue INR",
                    "SELECT m.title,COALESCE(SUM(v.revenue),0) FROM movies m LEFT JOIN shows s ON s.movie_id=m.id LEFT JOIN show_summary v ON v.id=s.id GROUP BY m.id,m.title ORDER BY m.id");
            default -> System.out.println("Enter a number from 0 to 9.");
        }
    }

    private static void schedule(EntityManager db,Input in) {
        long movie=in.id("Movie ID"), screen=in.id("Screen ID");
        String start=in.text("Show start YYYY-MM-DD HH:MM");
        BigDecimal price=in.price();
        tx(db,em -> {
            query(em,"SELECT id FROM screens WHERE id=?1 FOR UPDATE",screen).getSingleResult();
            long overlaps=((Number)query(em,"""
                SELECT count(*) FROM shows s JOIN movies old ON old.id=s.movie_id
                CROSS JOIN movies fresh
                WHERE s.screen_id=?1 AND fresh.id=?2
                 AND s.starts_at<CAST(?3 AS timestamp)+make_interval(mins=>fresh.duration_minutes)
                 AND s.starts_at+make_interval(mins=>old.duration_minutes)>CAST(?3 AS timestamp)
                """,screen,movie,start).getSingleResult()).longValue();
            if(overlaps>0) throw new IllegalArgumentException("This screen already has an overlapping show.");
            return query(em,"INSERT INTO shows(movie_id,screen_id,starts_at,ticket_price) VALUES (?1,?2,CAST(?3 AS timestamp),?4)",
                    movie,screen,start,price).executeUpdate();
        });
        System.out.println("Show scheduled.");
    }

    private static void book(EntityManager db,Input in) {
        long show=in.id("Show ID");
        int tickets=in.count("Number of tickets");
        String name=in.text("Customer name"), contact=in.text("Customer contact");
        String result=tx(db,em -> {
            Object[] showRow=(Object[])query(em,"SELECT ticket_price, starts_at>LOCALTIMESTAMP FROM shows WHERE id=?1 FOR UPDATE",show).getSingleResult();
            if(!Boolean.TRUE.equals(showRow[1])) return "Booking rejected: show has started.";
            long available=((Number)query(em,"SELECT capacity-booked FROM show_summary WHERE id=?1",show).getSingleResult()).longValue();
            if(tickets>available) return "Booking rejected. Available seats: "+available;
            long customer=((Number)query(em,"INSERT INTO customers(name,contact) VALUES (?1,?2) ON CONFLICT (contact) DO UPDATE SET name=EXCLUDED.name RETURNING id",name,contact).getSingleResult()).longValue();
            long booking=((Number)query(em,"INSERT INTO bookings(show_id,customer_id,tickets) VALUES (?1,?2,?3) RETURNING id",show,customer,tickets).getSingleResult()).longValue();
            BigDecimal amount=((BigDecimal)showRow[0]).multiply(BigDecimal.valueOf(tickets));
            query(em,"INSERT INTO payments(booking_id,paid) VALUES (?1,?2)",booking,amount).executeUpdate();
            return "Confirmed booking "+booking+" | Recorded payment INR "+amount;
        });
        System.out.println(result);
    }

    private static void cancel(EntityManager db,Input in) {
        long booking=in.id("Booking ID");
        String result=tx(db,em -> {
            long show=((Number)query(em,"SELECT show_id FROM bookings WHERE id=?1",booking).getSingleResult()).longValue();
            boolean refundable=Boolean.TRUE.equals(query(em,"SELECT starts_at>LOCALTIMESTAMP FROM shows WHERE id=?1 FOR UPDATE",show).getSingleResult());
            if(!refundable) return "Cancellation rejected: show has started.";
            int changed=query(em,"UPDATE bookings SET cancelled_at=LOCALTIMESTAMP WHERE id=?1 AND cancelled_at IS NULL",booking).executeUpdate();
            if(changed==0) return "Booking already cancelled.";
            Object refund=query(em,"UPDATE payments SET refunded=paid WHERE booking_id=?1 RETURNING refunded",booking).getSingleResult();
            return "Cancelled. Seats released. Recorded refund INR "+refund;
        });
        System.out.println(result);
    }

    private static void add(EntityManager db,String sql,Object... values) {
        tx(db,em -> query(em,sql,values).executeUpdate());
        System.out.println("Record added.");
    }

    private static Query query(EntityManager db,String sql,Object... values) {
        var q=db.createNativeQuery(sql);
        for(int i=0;i<values.length;i++) q.setParameter(i+1,values[i]);
        return q;
    }

    private static <T> T tx(EntityManager db,Function<EntityManager,T> operation) {
        var tx=db.getTransaction();
        tx.begin();
        try {
            T value=operation.apply(db);
            tx.commit();
            return value;
        } catch(RuntimeException ex) {
            if(tx.isActive()) tx.rollback();
            throw ex;
        }
    }

    private static void report(EntityManager db,String heading,String sql) {
        System.out.println(heading);
        var rows=query(db,sql).getResultList();
        if(rows.isEmpty()) System.out.println("No records.");
        for(Object row: rows) {
            System.out.println(String.join(" | ",Arrays.stream((Object[])row).map(String::valueOf).toList()));
        }
    }
}
