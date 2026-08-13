package ch.swisstopo.monteis.core.infrastructure.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Captures log output of a single logger for the duration of a test, restoring the original level
 * on {@link #close()}.
 */
final class LogCapture implements AutoCloseable {

  private final Logger logger;
  private final Level originalLevel;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private LogCapture(Class<?> type, Level level) {
    this.logger = (Logger) LoggerFactory.getLogger(type);
    this.originalLevel = logger.getLevel();
    logger.setLevel(level);
    appender.start();
    logger.addAppender(appender);
  }

  static LogCapture of(Class<?> type, Level level) {
    return new LogCapture(type, level);
  }

  /** Formatted messages captured so far. */
  List<String> messages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setLevel(originalLevel);
  }
}
