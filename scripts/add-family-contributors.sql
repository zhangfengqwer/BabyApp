ALTER TABLE "Moment" ADD COLUMN IF NOT EXISTS "contributorIds" UUID[] NOT NULL DEFAULT '{}';
UPDATE "Moment" SET "contributorIds" = ARRAY["authorId"] WHERE cardinality("contributorIds") = 0;
