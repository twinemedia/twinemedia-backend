CREATE TABLE public.apikeys
(
    id serial NOT NULL,
    key_id character varying(10) NOT NULL,
    key_name character varying(64) NOT NULL,
    key_permissions jsonb NOT NULL DEFAULT '[]'::jsonb,
    key_jwt text NOT NULL,
    key_owner integer NOT NULL,
    key_created_on timestamp with time zone NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);