-- esg_indicators: ESG S/G 지표 마스터 (KSSB 2 프레임워크 기준)
CREATE TABLE esg_indicators (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL UNIQUE,
    framework       VARCHAR(50)  NOT NULL DEFAULT 'KSSB2',
    category        VARCHAR(20)  NOT NULL,        -- E, S, G
    sub_category    VARCHAR(100),
    name_ko         VARCHAR(200) NOT NULL,
    unit            VARCHAR(50),
    data_type       VARCHAR(30)  NOT NULL DEFAULT 'NUMERIC',
    is_required     BOOLEAN      NOT NULL DEFAULT FALSE,
    disclosure_ref  VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_indicators_framework ON esg_indicators (framework, category);

-- unit_conversions: 단위 변환 계수 테이블
CREATE TABLE unit_conversions (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    from_unit       VARCHAR(30)   NOT NULL,
    to_unit         VARCHAR(30)   NOT NULL,
    factor          NUMERIC(20,10) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (from_unit, to_unit)
);

INSERT INTO unit_conversions (from_unit, to_unit, factor) VALUES
    ('GJ',  'kWh',  277.7777777778),
    ('kWh', 'GJ',   0.0036),
    ('TJ',  'GJ',   1000),
    ('GJ',  'TJ',   0.001),
    ('Mcal','GJ',   0.0041868),
    ('GJ',  'Mcal', 238.8458966),
    ('MWh', 'GJ',   3.6),
    ('GJ',  'MWh',  0.2777777778),
    ('ton', 'kg',   1000),
    ('kg',  'ton',  0.001),
    ('kL',  'L',    1000),
    ('L',   'kL',   0.001);
