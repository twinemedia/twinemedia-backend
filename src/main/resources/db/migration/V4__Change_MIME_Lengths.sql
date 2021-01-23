ALTER TABLE public.media
    ALTER COLUMN media_mime TYPE character varying(64);
ALTER TABLE public.lists
    ALTER COLUMN list_source_mime TYPE character varying(64);