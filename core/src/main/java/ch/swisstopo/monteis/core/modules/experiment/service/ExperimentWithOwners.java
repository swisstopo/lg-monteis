package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;

/** An experiment together with the owners the caller gets to see. */
public record ExperimentWithOwners(Experiment experiment, VisibleOwners owners) {}
