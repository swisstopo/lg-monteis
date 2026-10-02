package ch.swisstopo.monteis.core.modules.experiment.domain;

/** A file about to be stored as an {@link ExperimentDocument}. */
public record DocumentUpload(String fileName, String contentType, long sizeBytes) {}
