package ppl.momofin.momofinbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ppl.momofin.momofinbackend.model.Log;
import ppl.momofin.momofinbackend.repository.LogRepository;

import java.time.LocalDateTime;

@Service
public class LoggingServiceImpl implements LoggingService{

    private final LogRepository logRepository;

    @Autowired
    public LoggingServiceImpl(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public void log(Long userId,String level, String message, String logName, String sourceUrl) {
        Log logEntry = new Log(userId, LocalDateTime.now(), level, message, logName, sourceUrl);
        logRepository.save(logEntry);
    }
}
