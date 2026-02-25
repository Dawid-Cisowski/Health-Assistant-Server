CREATE TABLE examination_links (
    id_a       UUID        NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    id_b       UUID        NOT NULL REFERENCES examinations(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_examination_links PRIMARY KEY (id_a, id_b),
    CONSTRAINT chk_examination_links_order CHECK (id_a::text < id_b::text),
    CONSTRAINT chk_examination_links_no_self CHECK (id_a <> id_b)
);
CREATE INDEX idx_examination_links_id_a ON examination_links(id_a);
CREATE INDEX idx_examination_links_id_b ON examination_links(id_b);
