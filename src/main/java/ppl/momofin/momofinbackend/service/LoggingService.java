package ppl.momofin.momofinbackend.service;

public interface LoggingService {
    void log(Long userId, String level, String message, String logName, String sourceUrl);
}
