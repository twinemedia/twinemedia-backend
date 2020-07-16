ALTER TABLE public.accounts
    ADD COLUMN account_exclude_tags jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.accounts
    ADD COLUMN account_exclude_other_media boolean NOT NULL DEFAULT false;

ALTER TABLE public.accounts
    ADD COLUMN account_exclude_other_lists boolean NOT NULL DEFAULT false;

ALTER TABLE public.accounts
    ADD COLUMN account_exclude_other_tags boolean NOT NULL DEFAULT false;

ALTER TABLE public.accounts
    ADD COLUMN account_exclude_other_processes boolean NOT NULL DEFAULT false;

DROP MATERIALIZED VIEW public.tags;
CREATE MATERIALIZED VIEW public.tags
 AS
SELECT
	btrim(tags.tag, '"'::text) AS tag_name,
	tags.tag_creator,
	(
		SELECT COUNT(*) FROM media
		WHERE
		media_tags::jsonb ? btrim(tags.tag, '"'::text)
		AND media_creator = tags.tag_creator
	) AS tag_files
FROM (
	SELECT DISTINCT ON
			(
				(
					(jsonb_array_elements(media.media_tags)::text || ' '::text) || media.media_creator
				)
			) jsonb_array_elements(media.media_tags)::text AS tag,
			media.media_creator AS tag_creator
    	FROM media
	) tags
 WITH DATA;