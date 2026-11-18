-- As local-docker/postgres: the container's user (audit_owner) owns crm_audit_db; these two only get what the
-- audit migrations grant them.
CREATE USER audit_writer WITH PASSWORD 'writer';
CREATE USER audit_reader WITH PASSWORD 'reader';
GRANT CONNECT ON DATABASE crm_audit_db TO audit_writer, audit_reader;
