CREATE TABLE public.accounts
(
    id serial NOT NULL,
    account_email character varying(64) NOT NULL,
    account_name character varying(64) NOT NULL,
    account_permissions jsonb NOT NULL DEFAULT '[]'::json,
    account_admin boolean NOT NULL DEFAULT false,
    account_hash text NOT NULL,
    account_creation_date timestamp with time zone NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE TABLE public.media
(
    id bigserial NOT NULL,
    media_id character varying(10) NOT NULL,
    media_name character varying(256),
    media_filename character varying(256) NOT NULL,
    media_size bigint NOT NULL,
    media_mime character varying(32) NOT NULL,
    media_tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    media_file character varying(256) NOT NULL,
    media_created_on timestamp with time zone NOT NULL DEFAULT NOW(),
    media_description character varying(1024),
    media_meta jsonb NOT NULL DEFAULT '{}'::jsonb,
    media_creator integer NOT NULL,
    media_parent integer,
    media_file_hash text NOT NULL,
    media_thumbnail_file character varying(256),
    media_thumbnail boolean NOT NULL DEFAULT false,
    media_processing boolean NOT NULL DEFAULT false,
    media_process_error text,
    PRIMARY KEY (id)
);

CREATE TABLE public.processes
(
    id serial NOT NULL,
    process_mime character varying(120) NOT NULL,
    process_settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    process_created_on timestamp with time zone NOT NULL DEFAULT NOW(),
    process_creator integer NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE public.lists
(
    id serial NOT NULL,
    list_id character varying(10) NOT NULL,
    list_name character varying(256) NOT NULL,
    list_description character varying(1024),
    list_creator integer NOT NULL,
    list_type integer NOT NULL DEFAULT 0,
    list_visibility integer NOT NULL DEFAULT 0,
    list_created_on timestamp with time zone NOT NULL DEFAULT NOW(),
    list_source_tags jsonb,
    list_source_exclude_tags jsonb,
    list_source_mime character varying(32),
    list_source_created_before timestamp with time zone,
    list_source_created_after timestamp with time zone,
    PRIMARY KEY (id)
);

CREATE TABLE public.listitems
(
    id bigserial NOT NULL,
    item_media integer NOT NULL,
    item_list integer NOT NULL,
    PRIMARY KEY (id)
);

CREATE MATERIALIZED VIEW public.tags
AS
 SELECT btrim(tags.tag, '"'::text) AS tag_name
   FROM ( SELECT DISTINCT ON ((json_array_elements(media.media_tags::json)::text)) json_array_elements(media.media_tags::json)::text AS tag
           FROM media) tags
WITH NO DATA;