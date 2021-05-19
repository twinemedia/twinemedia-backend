CREATE TABLE public.sources
(
    id serial NOT NULL,
    source_type character varying(256) NOT NULL,
    source_name character varying(256) NOT NULL,
    source_config jsonb NOT NULL,
    source_creator integer NOT NULL,
    source_global boolean NOT NULL DEFAULT false,
    PRIMARY KEY (id)
);

ALTER TABLE public.media
    RENAME media_file TO media_key;

ALTER TABLE public.media
    RENAME media_file_hash TO media_hash;

ALTER TABLE public.media
    ADD COLUMN media_source integer NOT NULL DEFAULT -1;

ALTER TABLE public.accounts
    ADD COLUMN account_default_source integer NOT NULL DEFAULT -1;
