-- V20__activity_data_correction.sql
-- 정정 컬럼 추가 (P1: 새 버전 INSERT 패턴 — 원본 ARCHIVED, 정정본 신규 INSERT)
ALTER TABLE activity_data
  ADD COLUMN correction_of     UUID REFERENCES activity_data(id),
  ADD COLUMN correction_reason TEXT;

-- ARCHIVED 상태: 정정된 원본 레코드 표식
-- V7 생성 시 status 컬럼에 CHECK 제약 없음 → 애플리케이션 레이어에서 상태 전이 통제
CREATE INDEX idx_activity_data_correction_of ON activity_data(correction_of)
  WHERE correction_of IS NOT NULL;
