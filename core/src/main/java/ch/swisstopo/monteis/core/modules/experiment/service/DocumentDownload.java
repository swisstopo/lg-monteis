package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.modules.experiment.domain.ExperimentDocument;
import java.io.InputStream;

/** A document together with its opened content; the caller closes {@code content}. */
public record DocumentDownload(ExperimentDocument document, InputStream content) {}
