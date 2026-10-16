# MONTEIS

MONTEIS manages monitoring experiments, their sensors and documents, and who may see or change them.

## Experiments

**Experiment**:
A monitoring campaign with a name, a period and a comment, the unit that access and ownership are granted on.

**Master data**:
The descriptive fields of an Experiment (name, period, comment, Experiment Owners), as opposed to its sensors and measurements.
_Avoid_: Stammdaten, metadata

**Experiment document**:
A file a PI uploaded to a specific Experiment.
_Avoid_: Document (alone)

## Documents from Fulcrum

**Fulcrum entry**:
An entry of the Mont Terri literature that is maintained in Fulcrum and marked for MONTEIS, made up of citation metadata (number, title, authors, year and so on) and exactly one Fulcrum Document; it only exists in MONTEIS once that document was copied, and it belongs to no Experiment.
_Avoid_: Literature reference, Fulcrum record, Fulcrum Document

**Citation**:
The bibliographic data of a Fulcrum entry (number, title, authors, year, category, keywords), without its document.
_Avoid_: Details, metadata (ambiguous with the document's file metadata)

**Fulcrum Document**:
The one file of a Fulcrum entry, copied from Fulcrum into MONTEIS; when Fulcrum lists several, the first one is taken.
_Avoid_: Fulcrum file, attachment (Fulcrum's term for a file not yet copied), Experiment document

## People and access

**PI**:
A user with write access to a specific Experiment.
_Avoid_: Principal investigator (spelled out), editor

**Experiment Owner**:
A PI of an Experiment who is listed as the contact person for questions about it; being an Experiment Owner grants no permissions.
_Avoid_: Owner (alone, ambiguous with the former creator column), responsible, contact

**Owner candidate**:
A PI of an Experiment who can be picked as its Experiment Owner.

**Contact details**:
First name, last name and e-mail of a user, always read from Keycloak and never stored in MONTEIS.
_Avoid_: Profile, user data
