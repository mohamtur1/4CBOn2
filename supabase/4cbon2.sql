-- 4CBON2 AI Rewriter memory and public access schema.
-- Run in the Supabase SQL editor. Keep the service-role key server-side only.

-- Beliefs table (for L8)
CREATE TABLE IF NOT EXISTS beliefs (
  id BIGSERIAL PRIMARY KEY,
  belief TEXT NOT NULL,
  score_before INTEGER,
  score_after INTEGER,
  run_number INTEGER,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Questions table (for L9)
CREATE TABLE IF NOT EXISTS questions (
  id BIGSERIAL PRIMARY KEY,
  run_id TEXT,
  question_text TEXT NOT NULL,
  question_level INTEGER,
  question_type TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Feedback table (for validated critiques)
CREATE TABLE IF NOT EXISTS feedback (
  id BIGSERIAL PRIMARY KEY,
  evidence TEXT NOT NULL,
  confidence INTEGER CHECK (confidence BETWEEN 1 AND 5),
  critique_type TEXT,
  suggested_correction TEXT,
  run_id TEXT,
  injected BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Run limits table (for public version's 3 free tests)
CREATE TABLE IF NOT EXISTS run_limits (
  id BIGSERIAL PRIMARY KEY,
  ip TEXT NOT NULL,
  run_date DATE NOT NULL,
  run_count INTEGER NOT NULL DEFAULT 0,
  UNIQUE(ip, run_date)
);

-- Subscriptions table (for Gumroad webhook)
CREATE TABLE IF NOT EXISTS subscriptions (
  id BIGSERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  subscription_id TEXT,
  product_name TEXT,
  status TEXT,
  sale_id TEXT,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Row-level security: only the service role can write; public can read.
ALTER TABLE beliefs ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE run_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Service role can insert beliefs" ON beliefs FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert questions" ON questions FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert feedback" ON feedback FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert run_limits" ON run_limits FOR INSERT WITH CHECK (auth.role() = 'service_role');
CREATE POLICY "Service role can insert subscriptions" ON subscriptions FOR INSERT WITH CHECK (auth.role() = 'service_role');

CREATE POLICY "Public can read beliefs" ON beliefs FOR SELECT USING (true);
CREATE POLICY "Public can read questions" ON questions FOR SELECT USING (true);
CREATE POLICY "Public can read feedback" ON feedback FOR SELECT USING (true);
CREATE POLICY "Public can read run_limits" ON run_limits FOR SELECT USING (true);
CREATE POLICY "Public can read subscriptions" ON subscriptions FOR SELECT USING (true);
