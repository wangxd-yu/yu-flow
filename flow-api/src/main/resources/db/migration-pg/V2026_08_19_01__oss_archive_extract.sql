-- 2026-08-19：OSS zip 异步展开（场景开关 + 台账父子关系）
-- 与 db/migration/V2026_08_19_01__oss_archive_extract.sql 语义等价

ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_archive_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_keep_archive boolean NOT NULL DEFAULT true;
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_reject_policy varchar(32) NOT NULL DEFAULT 'SKIP_ZERO_FAIL';
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_allowed_extensions varchar(512);
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_allowed_content_types text;
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_max_entries integer;
ALTER TABLE flow_oss_upload_profile
    ADD COLUMN IF NOT EXISTS extract_max_uncompressed_bytes bigint;

COMMENT ON COLUMN flow_oss_upload_profile.extract_archive_enabled IS '上传 zip 后是否异步展开';
COMMENT ON COLUMN flow_oss_upload_profile.extract_keep_archive IS '展开成功后是否保留原包';
COMMENT ON COLUMN flow_oss_upload_profile.extract_reject_policy IS 'SKIP_ZERO_FAIL / FAIL_PACK';
COMMENT ON COLUMN flow_oss_upload_profile.extract_allowed_extensions IS '展开后落库扩展名白名单';
COMMENT ON COLUMN flow_oss_upload_profile.extract_allowed_content_types IS '展开后落库 MIME 白名单';
COMMENT ON COLUMN flow_oss_upload_profile.extract_max_entries IS '单包最多处理条目数，空=用全局';
COMMENT ON COLUMN flow_oss_upload_profile.extract_max_uncompressed_bytes IS '单包解压后总字节上限，空=用全局';

ALTER TABLE flow_oss_object
    ADD COLUMN IF NOT EXISTS parent_object_id varchar(32);
ALTER TABLE flow_oss_object
    ADD COLUMN IF NOT EXISTS archive_entry_path varchar(512);
ALTER TABLE flow_oss_object
    ADD COLUMN IF NOT EXISTS extract_status varchar(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE flow_oss_object
    ADD COLUMN IF NOT EXISTS extract_error varchar(512);

COMMENT ON COLUMN flow_oss_object.parent_object_id IS '来源压缩包台账 ID，空=独立上传';
COMMENT ON COLUMN flow_oss_object.archive_entry_path IS '包内相对路径（正斜杠）';
COMMENT ON COLUMN flow_oss_object.extract_status IS 'NONE / PENDING / EXTRACTING / DONE / FAILED / SKIPPED';
COMMENT ON COLUMN flow_oss_object.extract_error IS '展开结果摘要或失败原因';

CREATE INDEX IF NOT EXISTS idx_flow_oss_object_parent_object_id ON flow_oss_object (parent_object_id);
CREATE INDEX IF NOT EXISTS idx_flow_oss_object_extract_status ON flow_oss_object (extract_status);
