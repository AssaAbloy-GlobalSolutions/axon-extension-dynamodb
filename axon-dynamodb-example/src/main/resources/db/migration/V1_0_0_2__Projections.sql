create table public.priority_report
(
    id     int8 not null,
    high   int4 not null,
    low    int4 not null,
    medium int4 not null,
    primary key (id)
);

create table public.report
(
    id      int8 not null,
    created int4 not null,
    done    int4 not null,
    primary key (id)
);

create table public.todo
(
    id          varchar(255) not null,
    description varchar(255) not null,
    done        boolean      not null,
    priority    int4         not null,
    title       varchar(255) not null,
    primary key (id)
);
