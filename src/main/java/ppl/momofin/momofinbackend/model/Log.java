package ppl.momofin.momofinbackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "logs")
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;
    private String level;
    private String message;
    private String logName;

    private Long userId;
    private String sourceUrl;

    public Log(Long userId, LocalDateTime timestamp, String level, String message, String logName, String sourceUrl) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.logName = logName;
        this.sourceUrl = sourceUrl;
    }

    public Log() {

    }
}
