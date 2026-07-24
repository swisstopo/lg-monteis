package ch.swisstopo.monteis.core.modules.visualization.service;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Shared, thread-safe view of whether a tileset (see {@link TileUploadService}) exists yet. */
@Component
public class TileConversionStatus {

  public enum State {
    PENDING,
    READY
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
  private volatile Path outputDir;

  void markReady(Path readyOutputDir) {
    this.outputDir = readyOutputDir;
    state.set(State.READY);
  }

  public boolean isReady() {
    return state.get() == State.READY;
  }

  public State state() {
    return state.get();
  }

  public Path outputDir() {
    return outputDir;
  }
}
