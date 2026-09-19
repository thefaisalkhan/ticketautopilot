-- Full Choice/Score probability distributions from Jev, logged alongside the
-- single chosen category/urgency value for later analysis of borderline
-- calls. Only Jev populates these; the rule-based engine has no equivalent
-- distribution and leaves them NULL rather than fabricating one.
ALTER TABLE decisions
    ADD COLUMN category_probabilities JSONB,
    ADD COLUMN urgency_probabilities JSONB;
