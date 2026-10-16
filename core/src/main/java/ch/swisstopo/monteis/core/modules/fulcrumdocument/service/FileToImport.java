package ch.swisstopo.monteis.core.modules.fulcrumdocument.service;

import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow;
import ch.swisstopo.monteis.core.infrastructure.fulcrum.FulcrumLiteratureRow.FulcrumAttachmentReference;

// a new document, or a stored one whose file Fulcrum replaced
record FileToImport(FulcrumLiteratureRow row, FulcrumAttachmentReference attachment) {}
