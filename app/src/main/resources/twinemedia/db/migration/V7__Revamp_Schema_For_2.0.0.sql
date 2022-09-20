--- UTIL FUNCTIONS ---
create function pg_temp.gen_id() returns text as
$$
    select
        array_to_string(array(
            select substr('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-', ((random()*(64-1)+1)::integer), 1)
            from generate_series(1, 10)
        ), '')
$$ language sql;
--- END UTIL FUNCTIONS ---

-- Rename tags to "tags_view" so that we can still access it before deleting it
alter materialized view tags rename to tags_view;

-- Rename "media" to "files", but don't modify the schema until we're done using data from its original schema
alter table media rename to files;

-- Create tag-related tables
create table tags
(
    id              serial                                           not null,
    tag_name        varchar(256)                                     not null,
    tag_creator     integer,
    tag_file_count  integer                                not null default 0,
    tag_created_ts  timestamp with time zone default NOW()           not null,
    tag_modified_ts timestamp with time zone default NOW()           not null,
    constraint tags_pkey
        primary key (id),
    constraint creator_fk
        foreign key (tag_creator) references accounts (id)
        on delete set null
);
-- Tag names retain their original case, but two tags with the same content but different cases cannot exist
create unique index tag_name_and_creator_idx
    on tags (lower(tag_name), tag_creator);
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
        tag_id int;
        file_id int;
    begin
        -- Loop through all tags in the materialized view
        for key, name, creator in (select * from tags_view) loop
            -- Create record of the tag in the new tags table and get its ID
            insert into tags (tag_name, tag_creator) values (name, creator) returning id into tag_id;

            -- Create tag use records for all the files that have the tag
            for file_id in (select id from files where media_tags ? lower(name) and media_creator = creator) loop
                -- Create use link between file and tag, and use the media creation date as the link date
                -- The tag may not have been added to the file at that time, but it's more useful information than just defaulting to now()
                insert into tag_uses (use_tag, use_file, use_created_ts) values (tag_id, file_id, media_created_on);
            end loop;
        end loop;

        -- Update tag file counts
        update tags set tag_file_count = (select count(*) from tag_uses where use_tag = tags.id);
end $$;

-- We don't need the tags view anymore; delete it
drop materialized view tags_view;

-- Alter accounts table to reflect new schema changes
alter table accounts
    add account_id varchar(10) default pg_temp.gen_id() not null;
create unique index account_id_idx
    on accounts (account_id);
alter table accounts
    alter column account_id drop default;
create unique index account_email_idx
    on accounts (lower(account_email));
alter table accounts
    alter column account_default_source drop not null;
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
    add account_modified_ts timestamp with time zone default now() not null;
update accounts set account_modified_ts = account_created_ts;

-- Alter API keys table to reflect new schema changes
alter table apikeys
    rename to api_keys;
create unique index key_id_idx
    on api_keys (key_id);
alter table api_keys
    alter column key_name type varchar(256) using key_name::varchar(256);
alter table api_keys
    rename column key_owner to key_creator;
alter table api_keys
    alter column key_creator drop not null;
update api_keys set key_creator = null
    where (select count(*) from accounts where accounts.id = key_creator) < 1;
alter table api_keys
    add constraint key_creator_fk
        foreign key (key_creator) references accounts (id)
        on delete set null;
alter table api_keys
    rename column key_created_on to key_created_ts;
alter table api_keys
    add key_modified_ts timestamp with time zone default now() not null;
update api_keys set key_modified_ts = key_created_ts;

-- Alter files table to reflect new schema changes
alter table files
    drop column media_tags;
alter table files
    rename column media_id to file_id;
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
    alter column file_mime type varchar(129) using file_mime::varchar(129);
alter table files
    rename column media_key to file_key;
alter table files
    rename column media_created_on to file_created_ts;
alter table files
    rename column media_description to file_description;
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
    and (select count(*) from accounts where accounts.id = file_parent) < 1;
alter table files
    add constraint file_parent_fk
        foreign key (file_parent) references files (id)
        on delete restrict;
alter table files
    rename column media_hash to file_hash;
alter table files
    rename column media_thumbnail_file to file_thumbnail_key;
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
    add constraint file_source_fk
        foreign key (file_source) references sources (id)
        on delete restrict;

-- Convert hash format to hex for all files
update files SET file_hash = encode(decode(file_hash, 'base64'), 'hex');

-- Alter list items table to reflect new schema changes
alter table listitems
    rename to list_items;
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
alter table lists
    alter column list_creator drop not null;
update lists set list_creator = null
    where (select count(*) from accounts where accounts.id = list_creator) < 1;
alter table lists
    add constraint list_creator_fk
        foreign key (list_creator) references accounts (id)
        on delete set null;
alter table lists
    rename column list_created_on to list_created_ts;
alter table lists
    rename column list_modified_on to list_modified_ts;

-- Alter processes table to reflect new schema changes
alter table processes
    rename to process_presets;
alter table process_presets
    add preset_id varchar(10) default pg_temp.gen_id() not null;
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
alter table process_presets
    alter column preset_creator drop not null;
update process_presets set preset_creator = null
    where (select count(*) from accounts where accounts.id = preset_creator) < 1;
alter table process_presets
    add constraint process_preset_creator_fk
        foreign key (preset_creator) references accounts (id)
        on delete set null;
alter table process_presets
    rename column process_created_on to preset_created_ts;
alter table process_presets
    rename column process_modified_on to preset_modified_ts;
alter table process_presets
    add preset_name varchar(256) default '' not null;
update process_presets set preset_name = preset_mime;
alter table process_presets
    alter column preset_name drop default;

-- Alter sources table to reflect new schema changes
alter table sources
    add source_id varchar(10) default pg_temp.gen_id() not null;
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
    add source_modified_ts timestamp with time zone default now() not null;
update sources set source_modified_ts = source_created_ts;

-- Add counter columns to tables
alter table accounts
    add account_file_count integer not null default 0;
update accounts set account_file_count = (select count(*) from files where file_creator = accounts.id);
alter table sources
    add source_file_count integer not null default 0;
update sources set source_file_count = (select count(*) from files where file_source = sources.id);
alter table lists
    add list_item_count integer default null;
update lists set list_item_count = (select count(*) from list_items where item_list = lists.id)
    where list_type = 0; -- Only update count for standard lists

-- Update file counts on various tables
create function inc_file_counts() returns trigger as $$ begin
    update accounts set account_file_count = accounts.account_file_count + 1 where accounts.id = NEW.file_creator;
    update sources set source_file_count = source_file_count + 1 where sources.id = NEW.file_source;
    return null;
end $$ language plpgsql;
create function dec_file_counts() returns trigger as $$ begin
    update accounts set account_file_count = accounts.account_file_count - 1 where accounts.id = OLD.file_creator;
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
create function inc_tag_file_count() returns trigger as $$ begin
    update tags set tag_file_count = tag_file_count + 1 where tags.id = NEW.use_tag;
    return null;
end $$ language plpgsql;
create function dec_tag_file_count() returns trigger as $$ begin
    update tags set tag_file_count = tag_file_count - 1 where tags.id = OLD.use_tag;
    return null;
end $$ language plpgsql;
create trigger increment_tag_file_count
    after insert on tag_uses
    for each row
    execute procedure inc_tag_file_count();
create trigger decrement_tag_file_count
    after delete on tag_uses
    for each row
    execute procedure dec_tag_file_count();

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
--- END CLEAN UP UTIL FUNCTIONS ---