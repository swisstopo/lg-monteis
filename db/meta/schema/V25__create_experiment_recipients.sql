-- Recipients of an experiment's alarms (MON-245)
-- only keycloak user ids, contact details stay in keycloak.
-- list row = list saved once: PIs are Recipients until then, a saved empty list = nobody.
-- rows of users who lost access stay, filtered on read, Recipient again once access is back.

CREATE TABLE experiment_recipient_list
(
    experiment_id UUID PRIMARY KEY REFERENCES experiments (id) ON DELETE CASCADE
);

CREATE TABLE experiment_recipient
(
    experiment_id UUID NOT NULL REFERENCES experiment_recipient_list (experiment_id) ON DELETE CASCADE,
    user_id       UUID NOT NULL,
    PRIMARY KEY (experiment_id, user_id)
);

ALTER TABLE experiment_recipient_list ENABLE ROW LEVEL SECURITY;

CREATE POLICY experiment_recipient_list_read ON experiment_recipient_list FOR SELECT
    USING (can_access_experiment(experiment_recipient_list.experiment_id));
CREATE POLICY experiment_recipient_list_insert ON experiment_recipient_list FOR INSERT
    WITH CHECK (can_write_experiment(experiment_recipient_list.experiment_id));
CREATE POLICY experiment_recipient_list_delete ON experiment_recipient_list FOR DELETE
    USING (can_write_experiment(experiment_recipient_list.experiment_id));

ALTER TABLE experiment_recipient ENABLE ROW LEVEL SECURITY;

CREATE POLICY experiment_recipient_read ON experiment_recipient FOR SELECT
    USING (can_access_experiment(experiment_recipient.experiment_id));
CREATE POLICY experiment_recipient_insert ON experiment_recipient FOR INSERT
    WITH CHECK (can_write_experiment(experiment_recipient.experiment_id));
CREATE POLICY experiment_recipient_delete ON experiment_recipient FOR DELETE
    USING (can_write_experiment(experiment_recipient.experiment_id));
