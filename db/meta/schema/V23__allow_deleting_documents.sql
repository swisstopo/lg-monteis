-- -----------------------------------------------------------------------------
-- Deleting experiment and sensor documents (MON-173)
--
-- Whoever may add a document may delete it again, the delete policies repeat
-- the insert rules of V18 and V22. A row the policy hides is not deleted and
-- raises no error, the application treats zero deleted rows as forbidden.
-- The file in S3 is deleted by the application in the same transaction.
-- -----------------------------------------------------------------------------

CREATE POLICY experiment_documents_delete ON experiment_documents FOR DELETE
    USING (can_write_experiment(experiment_documents.experiment_id));

CREATE POLICY sensor_documents_delete ON sensor_documents FOR DELETE
    USING (can_write_an_experiment_of_sensor(sensor_documents.sensor_id));
