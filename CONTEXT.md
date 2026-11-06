# MONTEIS

MONTEIS manages monitoring experiments, their sensors and documents, and who may see or change them.

## Experiments

**Experiment**:
A monitoring campaign with a name, a period and a comment, the unit that access and ownership are granted on.

**Master data**:
The descriptive fields of an Experiment (name, period, comment, Experiment Owners), as opposed to its sensors and measurements.
_Avoid_: Stammdaten, metadata

**Experiment document**:
A file a PI uploaded to a specific Experiment; a Monteis-Admin or a PI of the Experiment can delete it, which removes it from MONTEIS for good.
_Avoid_: Document (alone)

## Sensors

**Sensor document**:
A file a Monteis-Admin or a PI of one of the Sensor's Experiments uploaded to a specific Sensor; everyone who can see the Sensor can see it, and the same people who can upload it can delete it for good.
_Avoid_: Document (alone), Attachment, Experiment document

**Fulcrum ID**:
The optional link from a Sensor to its record in Fulcrum; only a Sensor with a Fulcrum ID takes its coordinates from Fulcrum.
_Avoid_: Fulcrum record id, external id

**Fulcrum refresh**:
Taking the current coordinates from Fulcrum over into the selected Sensors, or every visible Sensor when none is selected, on request; PIs, global editors and admins may refresh every Sensor they can see, and a Sensor counts as updated only when its coordinates changed.
_Avoid_: Sync, import, Aktualisieren (alone)

**Last Fulcrum refresh**:
When MONTEIS last read a Sensor's coordinates from Fulcrum, whether or not they changed; a Sensor without a Fulcrum ID has none.
_Avoid_: Last sync, updated at

## Documents from Fulcrum

**Fulcrum Document**:
A document from the Mont Terri literature that is maintained in Fulcrum and marked for MONTEIS, made up of its metadata (number, title, authors, year, category, keywords) and exactly one file; it only exists in MONTEIS once that file was copied, and it belongs to no Experiment.
_Avoid_: Fulcrum entry, literature reference, citation, Experiment document, Document (alone)

**Attachment**:
Fulcrum's term for a file it holds; when Fulcrum lists several for one Fulcrum Document, the first one is taken.
_Avoid_: Fulcrum file

## People and access

**PI**:
A user with write access to a specific Experiment.
_Avoid_: Principal investigator (spelled out), editor, Experiment-Admin

**Monteis-Admin**:
A user who may see and change every Experiment and Sensor in MONTEIS, including their documents.
_Avoid_: Admin (alone), superuser

**Experiment Owner**:
A PI of an Experiment who is listed as the contact person for questions about it; being an Experiment Owner grants no permissions.
_Avoid_: Owner (alone, ambiguous with the former creator column), responsible, contact

**Owner candidate**:
A PI of an Experiment who can be picked as its Experiment Owner.

**Contact details**:
First name, last name and e-mail of a user, always read from Keycloak and never stored in MONTEIS.
_Avoid_: Profile, user data
