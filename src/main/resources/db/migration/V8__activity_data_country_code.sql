-- country_code는 배출계수 조회 키이므로 activity_data에 저장 필요 (06-emission-calculation.md)
ALTER TABLE activity_data ADD COLUMN country_code CHAR(2) NOT NULL DEFAULT 'KR';
