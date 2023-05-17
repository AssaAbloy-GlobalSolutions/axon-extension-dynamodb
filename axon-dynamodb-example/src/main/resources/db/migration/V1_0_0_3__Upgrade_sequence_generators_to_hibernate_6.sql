CREATE SEQUENCE pmx_event_sequence START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE association_value_entry_seq
    START WITH 1
    INCREMENT BY 50;

CREATE SEQUENCE domain_event_entry_seq
    START WITH 1
    INCREMENT BY 50;
--

SELECT setval('domain_event_entry_seq', (SELECT nextval('hibernate_sequence')));
SELECT setval('association_value_entry_seq', 1);
