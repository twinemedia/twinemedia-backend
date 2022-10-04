--- UTIL FUNCTIONS ---
create function pg_temp.gen_id() returns text as
$$ begin
    return (select
        array_to_string(array(
            select substr('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-', ((random()*(64-1)+1)::integer), 1)
            from generate_series(1, 10)
        ), '')
    );
end $$ language plpgsql;
create function pg_temp.jsonb_to_str_array(json_array jsonb) returns text[] as
$$ begin
    if json_array is null then
        return (select null::text[]);
    else
        return (
            select coalesce(array_agg(substr(value::text, 2, length(value::text)-2)), array[]::text[])
            from json_array_elements(json_array::json) json_vals
        );
    end if;
end $$ language plpgsql;
--- END UTIL FUNCTIONS ---

-- Rename tags to "tags_view" so that we can still access it before deleting it
alter materialized view tags
    rename to tags_view;

-- Rename "media" to "files", but don't modify the schema until we're done using data from its original schema
alter table media
    rename to files;
alter sequence media_id_seq
    rename to files_id_seq;

-- Create tag-related tables
create table tags
(
    id              serial                                           not null,
    tag_id          char(10)                                         not null,
    tag_name        varchar(256)                                     not null,
    tag_description varchar(1024)                         not null default '',
    tag_creator     integer,
    tag_file_count  integer                                not null default 0,
    tag_created_ts  timestamp with time zone default NOW(),
    tag_modified_ts timestamp with time zone default NOW(),
    constraint tags_pkey
        primary key (id),
    constraint creator_fk
        foreign key (tag_creator) references accounts (id)
        on delete set null
);
create unique index tag_name_and_id_idx
    on tags (tag_name, id);
create unique index tag_file_count_and_id_idx
    on tags (tag_file_count, id);
-- Tag names retain their original case, but two tags with the same content but different cases cannot exist
create unique index tag_name_and_creator_idx
    on tags (lower(tag_name), tag_creator);
create unique index tag_created_ts_and_id_idx
    on tags (tag_created_ts, id);
create unique index tag_modified_ts_and_id_idx
    on tags (tag_modified_ts, id);
create table tag_uses
(
    id             serial                                 not null,
    use_tag        integer                                not null
        constraint tag_fk
            references tags (id)
            on delete cascade,
    use_file       integer                                not null
        constraint file_fk
            references files (id)
            on delete cascade,
    use_created_ts timestamp with time zone default NOW() not null,
    constraint tag_uses_pkey
        primary key (id),
    constraint tag_uses_tag_and_file
        unique (use_tag, use_file)
);

-- Migrate tags from materialized view to new tags table
refresh materialized view tags_view;
do $$
    declare
        key text;
        name text;
        creator int;
        tag_pk int;
        file_id int;
        file_created_ts timestamp with time zone;
        creator_or_null int;
    begin
        -- Loop through all tags in the materialized view
        for key, name, creator in (select * from tags_view) loop
            -- If no creator account is found, use null as creator
            if (select count(*) from accounts where id = creator) > 1 then
                creator_or_null = null;
            else
                creator_or_null = creator;
            end if;

            -- Create record of the tag in the new tags table and get its ID
            insert into tags (tag_id, tag_name, tag_creator, tag_created_ts, tag_modified_ts) values (
                pg_temp.gen_id(),
                name,
                creator_or_null,
                null,
                null
            ) returning id into tag_pk;

            -- Create tag use records for all the files that have the tag
            for file_id, file_created_ts in (select id, media_created_on from files where media_tags ? lower(name) and media_creator = creator) loop
                -- Create use link between file and tag, and use the media creation date as the link date
                -- The tag may not have been added to the file at that time, but it's more useful information than just defaulting to now()
                insert into tag_uses (use_tag, use_file, use_created_ts) values (tag_pk, file_id, file_created_ts + (floor(random()*10) || ' seconds')::interval); -- Add a random number of seconds to the timestamp if it's not unique
            end loop;
        end loop;

        -- Update tag file counts and creation timestamps
        update tags set
            tag_file_count = (select count(*) from tag_uses where use_tag = tags.id),
            tag_created_ts = coalesce((select use_created_ts from tag_uses where use_tag = tags.id order by tag_created_ts limit 1), now()),
            tag_modified_ts = coalesce((select use_created_ts from tag_uses where use_tag = tags.id order by tag_created_ts limit 1), now());

        -- Disallow nulls now that timestamps are populated
        alter table tags
            alter column tag_created_ts set not null;
        alter table tags
            alter column tag_modified_ts set not null;
end $$;

-- We don't need the tags view anymore; delete it
drop materialized view tags_view;

-- Alter accounts table to reflect new schema changes
alter table accounts
    add account_id char(10) default pg_temp.gen_id() not null;
create unique index account_id_idx
    on accounts (account_id);
alter table accounts
    alter column account_id drop default;
create unique index account_email_idx
    on accounts (lower(account_email));
alter table accounts
    alter column account_permissions drop default;
alter table accounts
    alter column account_permissions type varchar(256)[] using pg_temp.jsonb_to_str_array(account_permissions);
alter table accounts
    alter column account_permissions set default array[]::varchar(256)[];
alter table accounts
    alter column account_exclude_tags drop default;
alter table accounts
    alter column account_exclude_tags type varchar(256)[] using pg_temp.jsonb_to_str_array(account_exclude_tags);
alter table accounts
    alter column account_exclude_tags set default array[]::varchar(256)[];
alter table accounts
    alter column account_default_source drop not null;
alter table accounts
    alter column account_default_source drop default;
update accounts set account_default_source = null
    where account_default_source = -1
or (select count(*) from sources where sources.id = account_default_source) < 1;
alter table accounts
    add constraint account_default_source_fk
        foreign key (account_default_source) references sources (id)
        on delete set null;
alter table accounts
    rename column account_creation_date to account_created_ts;
alter table accounts
    add account_modified_ts timestamp with time zone default now();
update accounts set account_modified_ts = account_created_ts;
alter table accounts
    alter column account_modified_ts set not null;
create unique index account_name_and_id_idx
    on accounts (account_name, id);
create unique index account_email_and_id_idx
    on accounts (lower(account_email), id);
create unique index account_created_ts_and_id_idx
    on accounts (account_created_ts, id);
create unique index account_modified_ts_and_id_idx
    on accounts (account_modified_ts, id);

-- Alter API keys table to reflect new schema changes
alter table apikeys
    rename to api_keys;
alter sequence apikeys_id_seq
    rename to api_keys_id_seq;
alter table api_keys
    alter column key_id type char(10);
create unique index api_key_id_idx
    on api_keys (key_id);
alter table api_keys
    alter column key_name type varchar(256);
alter table api_keys
    alter column key_permissions drop default;
alter table api_keys
    alter column key_permissions type varchar(256)[] using pg_temp.jsonb_to_str_array(key_permissions);
alter table api_keys
    alter column key_permissions set default array[]::varchar(256)[];
alter table api_keys
    rename column key_owner to key_creator;
delete from api_keys where (select count(*) from accounts where accounts.id = key_creator) < 1;
alter table api_keys
    add constraint key_creator_fk
        foreign key (key_creator) references accounts (id)
        on delete cascade;
alter table api_keys
    rename column key_created_on to key_created_ts;
alter table api_keys
    add key_modified_ts timestamp with time zone default now();
create unique index api_key_name_and_id_idx
    on api_keys (key_name, id);
update api_keys set key_modified_ts = key_created_ts;
alter table api_keys
    alter column key_modified_ts set not null;
create unique index api_key_created_ts_and_id_idx
    on api_keys (key_created_ts, id);
create unique index api_key_modified_ts_and_id_idx
    on api_keys (key_modified_ts, id);

-- Alter files table to reflect new schema changes
alter table files
    drop column media_tags;
alter table files
    rename column media_id to file_id;
alter table files
    alter column file_id type char(10);
create unique index file_id_idx
    on files (file_id);
alter table files
    rename column media_name to file_title;
alter table files
    rename column media_filename to file_name;
alter table files
    rename column media_size to file_size;
alter table files
    rename column media_mime to file_mime;
alter table files
    alter column file_mime type varchar(129);
alter table files
    rename column media_key to file_key;
alter table files
    rename column media_created_on to file_created_ts;
alter table files
    rename column media_description to file_description;
update files set file_description = '' where file_description is null;
alter table files
    alter column file_description set not null;
alter table files
    rename column media_meta to file_meta;
alter table files
    rename column media_creator to file_creator;
alter table files
    alter column file_creator drop not null;
update files set file_creator = null
    where (select count(*) from accounts where accounts.id = file_creator) < 1;
alter table files
    add constraint file_creator_fk
        foreign key (file_creator) references accounts (id)
        on delete set null;
alter table files
    rename column media_parent to file_parent;
update files set file_parent = null
    where file_parent is not null
    and (select count(*) from files parents where parents.id = files.file_parent) < 1;
alter table files
    add constraint file_parent_fk
        foreign key (file_parent) references files (id)
        on delete restrict;
alter table files
    rename column media_hash to file_hash;
alter table files
    rename column media_thumbnail_file to file_thumbnail_key;
alter table files
    alter column file_thumbnail_key type varchar(255);
alter table files
    drop column media_thumbnail;
alter table files
    rename column media_processing to file_processing;
alter table files
    rename column media_process_error to file_process_error;
alter table files
    rename column media_modified_on to file_modified_ts;
alter table files
    rename column media_source to file_source;
alter table files
    alter column file_source drop default;
alter table files
    add constraint file_source_fk
        foreign key (file_source) references sources (id)
        on delete restrict;
create unique index file_title_and_id_idx
    on files (file_title, id);
create unique index file_size_and_id_idx
    on files (file_size, id);
create unique index file_created_ts_and_id_idx
    on files (file_created_ts, id);
create unique index file_modified_ts_and_id_idx
    on files (file_modified_ts, id);

-- Convert hash format to hex for all files
update files SET file_hash = encode(decode(file_hash, 'base64'), 'hex');

-- Alter list items table to reflect new schema changes
alter table listitems
    rename to list_items;
alter sequence listitems_id_seq
    rename to list_items_id_seq;
alter table list_items
    rename column item_media to item_file;
alter table list_items
    add constraint list_item_file_fk
        foreign key (item_file) references files (id)
        on delete cascade;
alter table list_items
    add constraint list_item_list_fk
        foreign key (item_list) references lists (id)
        on delete cascade;
alter table list_items
    add constraint list_item_file_and_list_idx
        unique (item_file, item_list);
alter table list_items
    rename column item_created_on to item_created_ts;

-- Alter lists table to reflect new schema changes
create unique index list_id_idx
    on lists (list_id);
alter table lists
    alter column list_creator drop not null;
update lists set list_creator = null
    where (select count(*) from accounts where accounts.id = list_creator) < 1;
update lists set list_description = '' where lists.list_description is null;
alter table lists
    alter column list_description set not null;
alter table lists
    add constraint list_creator_fk
        foreign key (list_creator) references accounts (id)
        on delete set null;
alter table lists
    alter column list_source_tags drop default;
alter table lists
    alter column list_source_tags type varchar(256)[] using pg_temp.jsonb_to_str_array(list_source_tags);
alter table lists
    alter column list_source_tags set default array[]::varchar(256)[];
alter table lists
    alter column list_source_exclude_tags drop default;
alter table lists
    alter column list_source_exclude_tags type varchar(256)[] using pg_temp.jsonb_to_str_array(list_source_exclude_tags);
alter table lists
    alter column list_source_exclude_tags set default array[]::varchar(256)[];
alter table lists
    rename column list_created_on to list_created_ts;
alter table lists
    rename column list_modified_on to list_modified_ts;
create unique index list_name_and_id_idx
    on lists (list_name, id);
create unique index list_created_ts_and_id_idx
    on lists (list_created_ts, id);
create unique index list_modified_ts_and_id_idx
    on lists (list_modified_ts, id);

-- Alter processes table to reflect new schema changes
alter table processes
    rename to process_presets;
alter sequence processes_id_seq
    rename to process_presets_id_seq;
alter table process_presets
    add preset_id char(10) default pg_temp.gen_id() not null;
create unique index process_preset_id_idx
    on process_presets (preset_id);
alter table process_presets
    alter column preset_id drop default;
alter table process_presets
    rename column process_mime to preset_mime;
alter table process_presets
    rename column process_settings to preset_settings;
alter table process_presets
    rename column process_creator to preset_creator;
delete from process_presets where (select count(*) from accounts where accounts.id = preset_creator) < 1;
alter table process_presets
    add constraint process_preset_creator_fk
        foreign key (preset_creator) references accounts (id)
        on delete cascade;
alter table process_presets
    rename column process_created_on to preset_created_ts;
alter table process_presets
    rename column process_modified_on to preset_modified_ts;
alter table process_presets
    add preset_name varchar(256) default '' not null;
update process_presets set preset_name = preset_mime;
alter table process_presets
    alter column preset_name drop default;
create unique index process_preset_name_and_id_idx
    on process_presets (preset_name, id);
create unique index process_preset_created_ts_and_id_idx
    on process_presets (preset_created_ts, id);
create unique index process_preset_modified_ts_and_id_idx
    on process_presets (preset_modified_ts, id);

-- Alter sources table to reflect new schema changes
alter table sources
    add source_id char(10) default pg_temp.gen_id() not null;
create unique index source_id_idx
    on sources (source_id);
alter table sources
    alter column source_id drop default;
alter table sources
    alter column source_creator drop not null;
update sources set source_creator = null
    where (select count(*) from accounts where accounts.id = source_creator) < 1;
alter table sources
    add constraint source_creator_fk
        foreign key (source_creator) references accounts (id)
        on delete set null;
alter table sources
    rename column source_created_on to source_created_ts;
alter table sources
    add source_modified_ts timestamp with time zone default now();
update sources set source_modified_ts = sources.source_created_ts;
alter table sources
    alter column source_modified_ts set not null;
create unique index source_name_and_id_idx
    on sources (source_name, id);
create unique index source_created_ts_and_id_idx
    on sources (source_created_ts, id);
create unique index source_modified_ts_and_id_idx
    on sources (source_modified_ts, id);
update sources set source_modified_ts = source_created_ts;

-- Add counter columns to tables
alter table accounts
    add account_file_count integer not null default 0;
update accounts set account_file_count = (select count(*) from files where file_creator = accounts.id);
create unique index account_file_count_and_id_idx
    on accounts (account_file_count, id);
alter table files
    add file_tag_count integer not null default 0;
create unique index file_tag_count_and_id_idx
    on files (file_tag_count, id);
update files set file_tag_count = (select count(*) from tag_uses where use_file = files.id);
alter table files
    add file_child_count integer not null default 0;
create unique index file_child_count_and_id_idx
    on files (file_child_count, id);
update files parents set file_child_count = (select count(*) from files children where children.file_parent = parents.id);
alter table lists
    add list_item_count integer;
update lists set list_item_count = (select count(*) from list_items where item_list = lists.id)
    where list_type = 0; -- Only update count for standard lists
create unique index list_item_count_and_id_idx
    on lists (list_item_count, id);
alter table sources
    add source_file_count integer not null default 0;
update sources set source_file_count = (select count(*) from files where file_source = sources.id);
create unique index source_file_count_and_id_idx
    on sources (source_file_count, id);

-- Update file counts on various tables
create function inc_file_counts() returns trigger as $$ begin
    update accounts set account_file_count = accounts.account_file_count + 1 where accounts.id = NEW.file_creator;
    update files parents set file_child_count = file_child_count + 1 where parents.id = NEW.file_parent;
    update sources set source_file_count = source_file_count + 1 where sources.id = NEW.file_source;
    return null;
end $$ language plpgsql;
create function dec_file_counts() returns trigger as $$ begin
    update accounts set account_file_count = accounts.account_file_count - 1 where accounts.id = OLD.file_creator;
    update files parents set file_child_count = file_child_count - 1 where parents.id = OLD.file_parent;
    update sources set source_file_count = source_file_count - 1 where sources.id = OLD.file_source;
    return null;
end $$ language plpgsql;
create trigger increment_file_counts
    after insert on files
    for each row
    execute procedure inc_file_counts();
create trigger decrement_file_counts
    after delete on files
    for each row
    execute procedure dec_file_counts();

-- Update item count on lists table
create function inc_list_item_count() returns trigger as $$ begin
    update lists set list_item_count = list_item_count + 1
        where lists.id = NEW.item_list
        and list_type = 0;

    return null;
end $$ language plpgsql;
create function dec_list_item_count() returns trigger as $$ begin
    update lists set list_item_count = list_item_count - 1
        where lists.id = OLD.item_list
        and list_type = 0;
    return null;
end $$ language plpgsql;
create trigger increment_list_item_count
    after insert on list_items
    for each row
    execute procedure inc_list_item_count();
create trigger decrement_list_item_count
    after delete on list_items
    for each row
    execute procedure dec_list_item_count();

-- Update file count on tags table
create function inc_tag_and_file_count() returns trigger as $$ begin
    update tags set tag_file_count = tag_file_count + 1 where tags.id = NEW.use_tag;
    update files set file_tag_count = file_tag_count + 1 where files.id = NEW.use_file;
    return null;
end $$ language plpgsql;
create function dec_tag_and_file_count() returns trigger as $$ begin
    update tags set tag_file_count = tag_file_count - 1 where tags.id = OLD.use_tag;
    update files set file_tag_count = file_tag_count - 1 where files.id = OLD.use_file;
    return null;
end $$ language plpgsql;
create trigger increment_tag_and_file_count
    after insert on tag_uses
    for each row
    execute procedure inc_tag_and_file_count();
create trigger decrement_tag_file_count
    after delete on tag_uses
    for each row
    execute procedure dec_tag_and_file_count();

-- Perform actions based on list type changes
create function handle_list_type_change() returns trigger as $$ begin
    if NEW.list_type = 0 and OLD.list_type != 0 then
        -- Reset item count
        update lists set
            list_item_count = 0,
            list_source_tags = null,
            list_source_exclude_tags = null,
            list_source_created_before = null,
            list_source_created_after = null,
            list_source_mime = null
            where lists.id = OLD.id;
    elsif NEW.list_type = 1 and OLD.list_type != 1 then
        -- Clear item count and delete items
        update lists set list_item_count = null where lists.id = OLD.id;
        delete from list_items where item_list = OLD.id;
    end if;

    return null;
end $$ language plpgsql;
create trigger handle_list_type_change
    after update on lists
    for each row
    execute procedure handle_list_type_change();

--- CLEAN UP UTIL FUNCTIONS ---
drop function pg_temp.gen_id;
drop function pg_temp.jsonb_to_str_array;
--- END CLEAN UP UTIL FUNCTIONS ---