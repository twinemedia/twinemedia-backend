ALTER TABLE public.media
    ALTER COLUMN media_mime TYPE character varying(64);
ALTER TABLE public.lists
    ALTER COLUMN list_source_mime TYPE character varying(64);

DROP MATERIALIZED VIEW public.tags;
CREATE MATERIALIZED VIEW public.tags
 AS
SELECT
	CONCAT(CAST(tags.tag_creator AS TEXT), ':', btrim(tags.tag, '"'::text)) AS key,
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
 CREATE UNIQUE INDEX ON tags (key);

ALTER TABLE public.processes
    ADD COLUMN process_modified_on timestamp with time zone NOT NULL DEFAULT NOW();
UPDATE processes SET process_modified_on = process_created_on;

ALTER TABLE public.media
    ADD COLUMN media_modified_on timestamp with time zone NOT NULL DEFAULT NOW();
UPDATE media SET media_modified_on = media_created_on;

ALTER TABLE public.lists
    ADD COLUMN list_modified_on timestamp with time zone NOT NULL DEFAULT NOW();
UPDATE lists SET list_modified_on = list_created_on;

ALTER TABLE public.listitems
    ADD COLUMN item_created_on timestamp with time zone NOT NULL DEFAULT NOW();