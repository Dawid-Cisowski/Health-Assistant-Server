-- Fix AMYLASE and LIPASE category from LIVER_PANEL to PANCREATIC_PANEL (medically accurate)
UPDATE marker_definitions
SET category = 'PANCREATIC_PANEL'
WHERE code IN ('AMYLASE', 'LIPASE');
