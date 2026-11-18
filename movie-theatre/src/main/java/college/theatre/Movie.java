package college.theatre;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "movies")
public class Movie {
    @Id private Long id;
    private String title;
    private String genre;
    private String language;
    @Column(name = "duration_minutes") private int durationMinutes;
    @Column(name = "release_date") private LocalDate releaseDate;

    protected Movie() {}

    @Override
    public String toString() {
        return String.format("%d | %s | %s | %s | %d min | %s",
                id, title, genre, language, durationMinutes, releaseDate);
    }
}
