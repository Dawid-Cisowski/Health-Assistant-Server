-- Invalidate cached AI summaries so they regenerate with Markdown formatting
UPDATE daily_summaries
SET ai_summary = NULL, ai_summary_generated_at = NULL
WHERE ai_summary IS NOT NULL;
