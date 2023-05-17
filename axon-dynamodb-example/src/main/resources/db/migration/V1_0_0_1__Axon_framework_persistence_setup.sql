-- Default sequence used by hibernate for all @GeneratedValue ids without generator property
CREATE SEQUENCE "hibernate_sequence" START 1;
-- Sequence for domain event global indexes, mapped by orm.xml
CREATE SEQUENCE "cfx_event_sequence" START 1;


-- DOMAIN EVENT ENTRY
CREATE TABLE public.domain_event_entry
(
    "global_index"         int8         NOT NULL,
    "event_identifier"     varchar(255) NOT NULL,
    "meta_data"            json,
    "payload"              json         NOT NULL,
    "payload_revision"     varchar(255),
    "payload_type"         varchar(255) NOT NULL,
    "time_stamp"           varchar(255) NOT NULL,
    "aggregate_identifier" varchar(255) NOT NULL,
    "sequence_number"      int8         NOT NULL,
    "type"                 varchar(255),
    CONSTRAINT domain_event_entry_pkey PRIMARY KEY (global_index)
);

CREATE UNIQUE INDEX dee_unique_aggregate_sequence ON public.domain_event_entry USING btree (aggregate_identifier, sequence_number);
ALTER TABLE public.domain_event_entry
    ADD CONSTRAINT "dee_unique_event_id" UNIQUE ("event_identifier");


-- SNAPSHOT EVENT ENTRY
CREATE TABLE public.snapshot_event_entry
(
    "aggregate_identifier" varchar(255) NOT NULL,
    "sequence_number"      int8         NOT NULL,
    "type"                 varchar(255) NOT NULL,
    "event_identifier"     varchar(255) NOT NULL,
    "meta_data"            json,
    "payload"              json         NOT NULL,
    "payload_revision"     varchar(255),
    "payload_type"         varchar(255) NOT NULL,
    "time_stamp"           varchar(255) NOT NULL,
    CONSTRAINT "snapshot_event_entry_pkey" PRIMARY KEY (aggregate_identifier, sequence_number, type)
);

ALTER TABLE public.snapshot_event_entry
    ADD CONSTRAINT "see_unique_event_id" UNIQUE (event_identifier);


-- TOKEN ENTRY
CREATE TABLE public.token_entry
(
    processor_name varchar(255) NOT NULL,
    segment        integer      NOT NULL,
    owner          varchar(255),
    "timestamp"    varchar(255) NOT NULL,
    token          json,
    token_type     varchar(255),
    CONSTRAINT token_entry_pkey PRIMARY KEY (processor_name, segment)
);

-- SAGA ENTRY
CREATE TABLE public.saga_entry
(
    saga_id         varchar(255) NOT NULL,
    revision        varchar(255),
    saga_type       varchar(255),
    serialized_saga json,
    CONSTRAINT saga_entry_pkey PRIMARY KEY (saga_id)
);

-- SAGA ASSOCIATION VALUE ENTRY
CREATE TABLE public.association_value_entry
(
    id                bigint       NOT NULL,
    association_key   varchar(255) NOT NULL,
    association_value varchar(255),
    saga_id           varchar(255) NOT NULL,
    saga_type         varchar(255),
    CONSTRAINT association_value_entry_pkey PRIMARY KEY (id)
);

CREATE INDEX ave_saga_id_saga_type ON public.association_value_entry USING btree (saga_id, saga_type);
CREATE INDEX ave_saga_association ON public.association_value_entry USING btree (saga_type, association_key, association_value);