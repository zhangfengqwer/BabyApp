BEGIN;

CREATE TEMP TABLE moment_day_groups ON COMMIT DROP AS
SELECT
  id AS old_id,
  first_value(id) OVER (
    PARTITION BY "babyId", (("eventDate" AT TIME ZONE 'Asia/Shanghai')::date)
    ORDER BY "createdAt", id
  ) AS keep_id,
  count(*) OVER (
    PARTITION BY "babyId", (("eventDate" AT TIME ZONE 'Asia/Shanghai')::date)
  ) AS group_count
FROM "Moment";

-- Preserve every distinct text and location before duplicate moments are removed.
WITH unique_content AS (
  SELECT DISTINCT ON (g.keep_id, trim(m.content))
    g.keep_id, trim(m.content) AS content, m."createdAt"
  FROM moment_day_groups g
  JOIN "Moment" m ON m.id = g.old_id
  WHERE g.group_count > 1 AND nullif(trim(m.content), '') IS NOT NULL
  ORDER BY g.keep_id, trim(m.content), m."createdAt"
), combined AS (
  SELECT keep_id, string_agg(content, E'\n' ORDER BY "createdAt") AS content
  FROM unique_content
  GROUP BY keep_id
)
UPDATE "Moment" target
SET content = combined.content
FROM combined
WHERE target.id = combined.keep_id;

WITH locations AS (
  SELECT g.keep_id, left(string_agg(DISTINCT trim(m.location), '、'), 255) AS location
  FROM moment_day_groups g
  JOIN "Moment" m ON m.id = g.old_id
  WHERE g.group_count > 1 AND nullif(trim(m.location), '') IS NOT NULL
  GROUP BY g.keep_id
)
UPDATE "Moment" target
SET location = locations.location
FROM locations
WHERE target.id = locations.keep_id;

-- Remove duplicate references to the same Immich asset before moving assets.
WITH ranked_assets AS (
  SELECT ma.id,
    row_number() OVER (
      PARTITION BY g.keep_id, ma."immichAssetId"
      ORDER BY CASE WHEN ma."momentId" = g.keep_id THEN 0 ELSE 1 END, ma."createdAt", ma.id
    ) AS duplicate_number
  FROM "MomentAsset" ma
  JOIN moment_day_groups g ON g.old_id = ma."momentId"
  WHERE g.group_count > 1
)
DELETE FROM "MomentAsset" ma
USING ranked_assets ranked
WHERE ma.id = ranked.id AND ranked.duplicate_number > 1;

UPDATE "MomentAsset" ma
SET "momentId" = g.keep_id
FROM moment_day_groups g
WHERE ma."momentId" = g.old_id AND g.old_id <> g.keep_id;

WITH ordered_assets AS (
  SELECT id, row_number() OVER (
    PARTITION BY "momentId" ORDER BY "sortOrder", "createdAt", id
  ) - 1 AS new_order
  FROM "MomentAsset"
)
UPDATE "MomentAsset" ma
SET "sortOrder" = ordered.new_order
FROM ordered_assets ordered
WHERE ma.id = ordered.id;

UPDATE "Comment" comment
SET "momentId" = g.keep_id
FROM moment_day_groups g
WHERE comment."momentId" = g.old_id AND g.old_id <> g.keep_id;

-- The same user may have liked more than one duplicate moment. Keep one like per day.
WITH ranked_likes AS (
  SELECT liked.id,
    row_number() OVER (
      PARTITION BY g.keep_id, liked."userId"
      ORDER BY CASE WHEN liked."momentId" = g.keep_id THEN 0 ELSE 1 END, liked."createdAt", liked.id
    ) AS duplicate_number
  FROM "Like" liked
  JOIN moment_day_groups g ON g.old_id = liked."momentId"
  WHERE g.group_count > 1
)
DELETE FROM "Like" liked
USING ranked_likes ranked
WHERE liked.id = ranked.id AND ranked.duplicate_number > 1;

UPDATE "Like" liked
SET "momentId" = g.keep_id
FROM moment_day_groups g
WHERE liked."momentId" = g.old_id AND g.old_id <> g.keep_id;

DELETE FROM "Moment" moment
USING moment_day_groups g
WHERE moment.id = g.old_id AND g.old_id <> g.keep_id;

COMMIT;
