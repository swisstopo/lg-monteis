package ch.swisstopo.monteis.core;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class E2ECoreApplication {
  public static void main(String[] args) {
    SpringApplication.from(CoreApplication::main).with(TestcontainersConfiguration.class).run(args);
  }
}
