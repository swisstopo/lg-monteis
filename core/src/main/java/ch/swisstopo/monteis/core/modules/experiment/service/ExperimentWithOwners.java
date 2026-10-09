package ch.swisstopo.monteis.core.modules.experiment.service;

import ch.swisstopo.monteis.core.modules.experiment.domain.Experiment;
import ch.swisstopo.monteis.core.modules.experiment.query.VisibleOwners;

public record ExperimentWithOwners(Experiment experiment, VisibleOwners owners) {}
