-- consolidated_emission_contributions.ownership_ratio 범위 제약 (0 초과 1 이하)
-- Operational Control 포함 법인은 ownershipRatio=1, 루트 법인도 ownershipRatio=1
ALTER TABLE consolidated_emission_contributions
    ADD CONSTRAINT chk_contribution_ownership_ratio
    CHECK (ownership_ratio IS NULL OR (ownership_ratio > 0 AND ownership_ratio <= 1));
