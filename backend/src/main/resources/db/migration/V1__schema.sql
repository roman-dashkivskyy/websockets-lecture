create table app_user (
    id            bigint generated always as identity primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null,
    display_name  varchar(100) not null
);

create table todo_list (
    id         bigint generated always as identity primary key,
    title      varchar(100) not null,
    visibility varchar(10)  not null check (visibility in ('PUBLIC', 'PRIVATE')),
    owner_id   bigint       not null references app_user (id),
    created_at timestamptz  not null default now()
);

create table todo_item (
    id         bigint generated always as identity primary key,
    list_id    bigint       not null references todo_list (id) on delete cascade,
    title      varchar(200) not null,
    done       boolean      not null default false,
    created_at timestamptz  not null default now()
);

create index todo_list_owner_idx on todo_list (owner_id);
create index todo_item_list_idx on todo_item (list_id);
