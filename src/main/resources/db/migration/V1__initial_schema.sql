-- Spring Modulith: Outbox 패턴 이벤트 발행 추적 테이블 (spring-modulith-starter-jpa 필수)
-- Spring Modulith 2.0.0: last_resubmission_date, status 컬럼 필수
CREATE TABLE event_publication (
    id                      UUID         NOT NULL,
    listener_id             VARCHAR(512) NOT NULL,
    event_type              VARCHAR(512) NOT NULL,
    serialized_event        TEXT         NOT NULL,
    publication_date        TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date         TIMESTAMP WITH TIME ZONE,
    completion_attempts     INT          NOT NULL DEFAULT 0,
    last_resubmission_date  TIMESTAMP WITH TIME ZONE,
    status                  VARCHAR(36),
    PRIMARY KEY (id)
);

-- tenants: 멀티 테넌트 최상위 테이블
-- UUID는 JPA @GeneratedValue(strategy = GenerationType.UUID)로 앱에서 생성 (H2 호환)
CREATE TABLE tenants (
    id           UUID         PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    country_code CHAR(2)      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE
);

-- disclosure_schedules: 규제 의무 공시 일정 (전체 테넌트 공통 참조 테이블)
CREATE TABLE disclosure_schedules (
    id                  UUID         PRIMARY KEY,
    jurisdiction        VARCHAR(20)  NOT NULL,     -- KR, EU, GLOBAL
    framework           VARCHAR(30)  NOT NULL,     -- KSSB2, CSRD, ISSB_S2
    entity_criteria     VARCHAR(200),              -- "연결자산 30조 이상"
    mandatory_from_year INT          NOT NULL,
    scope3_from_year    INT,
    notes               TEXT,
    source_document     VARCHAR(200),
    last_updated        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (jurisdiction, framework, mandatory_from_year)
);
