-- DEAD LETTER ENTRY
create table public.dead_letter_entry
(
    dead_letter_id       character varying(255) primary key not null,
    cause_message        character varying(255),
    cause_type           character varying(255),
    diagnostics          json,
    enqueued_at          timestamp without time zone not null,
    sequence_index       bigint                             not null,
    last_touched         timestamp without time zone,
    aggregate_identifier character varying(255),
    event_identifier     character varying(255)             not null,
    message_type         character varying(255)             not null,
    meta_data            json,
    payload              json                               not null,
    payload_revision     character varying(255),
    payload_type         character varying(255)             not null,
    sequence_number      bigint,
    time_stamp           character varying(255)             not null,
    token                json,
    token_type           character varying(255),
    type                 character varying(255),
    processing_group     character varying(255)             not null,
    processing_started   timestamp without time zone,
    sequence_identifier  character varying(255)             not null
);
create index ave_processing_group on dead_letter_entry using btree (processing_group);
create index ave_processing_group_seq_id on dead_letter_entry using btree (processing_group, sequence_identifier);
create unique index ave_processing_group_seq_id_index on dead_letter_entry using btree (processing_group, sequence_identifier, sequence_index);

