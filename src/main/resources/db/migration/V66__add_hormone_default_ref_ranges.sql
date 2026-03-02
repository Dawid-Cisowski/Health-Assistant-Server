-- Add default reference ranges for hormones that previously had null/null.
-- These are "general" defaults used only as fallback when a lab document
-- does not provide its own reference range.
-- Sex- and cycle-specific ranges (ESTRADIOL, PROGESTERONE, LH, SHBG) stay null/null
-- because a single number would be misleading for those markers.

-- PROLACTIN: general reference (covers both sexes; women slightly higher)
UPDATE marker_definitions
SET ref_range_low_default  = 2.0,
    ref_range_high_default = 18.0
WHERE code = 'PROLACTIN';

-- CORTISOL: morning serum (8:00–10:00), the most common collection time in PL
UPDATE marker_definitions
SET ref_range_low_default  = 6.2,
    ref_range_high_default = 19.4
WHERE code = 'CORTISOL';

-- FSH: follicular phase / male range (widest commonly published range)
UPDATE marker_definitions
SET ref_range_low_default  = 1.5,
    ref_range_high_default = 12.4
WHERE code = 'FSH';

-- LH: follicular phase / male range
UPDATE marker_definitions
SET ref_range_low_default  = 1.7,
    ref_range_high_default = 8.6
WHERE code = 'LH';

-- DHEAS: adult reference (mid-range covers both sexes, broad)
UPDATE marker_definitions
SET ref_range_low_default  = 35.0,
    ref_range_high_default = 430.0
WHERE code = 'DHEAS';
