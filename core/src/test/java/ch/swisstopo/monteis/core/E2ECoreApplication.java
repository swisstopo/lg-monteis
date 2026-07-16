package ch.swisstopo.monteis.core;

import ch.swisstopo.monteis.core.itconfig.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class E2ECoreApplication {
  public static void main(String[] args) {
    SpringApplication.from(CoreApplication::main).with(TestcontainersConfiguration.class).run(args);
  }
}
