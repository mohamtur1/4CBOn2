-- ============================================================
-- 4CBON2 Supabase Schema
-- Run this in your Supabase SQL Editor
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- TABLE: pipeline_runs
-- Stores each deep pipeline execution
-- ============================================================
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id VARCHAR(255) UNIQUE NOT NULL,
    client_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Input
    original_answer TEXT NOT NULL,
    context TEXT,
    
    -- Scores
    score_before INTEGER,
    score_after INTEGER,
    score_delta INTEGER GENERATED ALWAYS AS (score_after - score_before) STORED,
    
    -- Status
    status VARCHAR(50) DEFAULT 'complete', -- complete, halted, error
    halt_reason TEXT,
    
    -- Final outputs
    final_answer TEXT,
    certification TEXT,
    
    -- Metadata
    mode VARCHAR(50), -- HIGH_QUALITY or STANDARD
    layers_completed INTEGER DEFAULT 0,
    runtime_ms INTEGER
);

-- Index for querying by client and time
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_client ON pipeline_runs(client_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_created ON pipeline_runs(created_at DESC);

-- ============================================================
-- TABLE: layer_outputs
-- Stores each layer's output from pipeline runs
-- ============================================================
CREATE TABLE IF NOT EXISTS layer_outputs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id VARCHAR(255) NOT NULL,
    layer_id VARCHAR(10) NOT NULL,
    layer_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Output
    output_text TEXT,
    output_length INTEGER,
    
    -- Layer metadata
    layer_order INTEGER,
    is_final BOOLEAN DEFAULT FALSE
);

-- Index for joining with pipeline_runs
CREATE INDEX IF NOT EXISTS idx_layer_outputs_run ON layer_outputs(run_id);
CREATE INDEX IF NOT EXISTS idx_layer_outputs_layer ON layer_outputs(layer_id);

-- ============================================================
-- TABLE: agent_sessions
-- Stores multi-agent orchestration sessions
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    client_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Session data
    goal TEXT,
    agent_type VARCHAR(100),
    
    -- Results
    subtask_count INTEGER DEFAULT 0,
    final_answer TEXT,
    status VARCHAR(50) DEFAULT 'active'
);

CREATE INDEX IF NOT EXISTS idx_agent_sessions_client ON agent_sessions(client_id);
CREATE INDEX IF NOT EXISTS idx_agent_sessions_created ON agent_sessions(created_at DESC);

-- ============================================================
-- TABLE: subtasks
-- Stores individual subtasks from agent sessions
-- ============================================================
CREATE TABLE IF NOT EXISTS subtasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Task info
    step_number INTEGER,
    subtask_text TEXT,
    specialist_agent VARCHAR(255),
    instructions TEXT,
    
    -- Result
    result TEXT,
    result_length INTEGER,
    tools_used TEXT[], -- Array of tool names used
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms INTEGER
);

CREATE INDEX IF NOT EXISTS idx_subtasks_session ON subtasks(session_id);

-- ============================================================
-- TABLE: gumroad_licenses
-- Stores verified Gumroad license keys
-- ============================================================
CREATE TABLE IF NOT EXISTS gumroad_licenses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    license_key VARCHAR(255) UNIQUE NOT NULL,
    product_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_verified_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Usage tracking
    uses_count INTEGER DEFAULT 0,
    last_used_by VARCHAR(255),
    last_used_at TIMESTAMP WITH TIME ZONE,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    max_uses INTEGER, -- NULL = unlimited
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_licenses_key ON gumroad_licenses(license_key);
CREATE INDEX IF NOT EXISTS idx_licenses_product ON gumroad_licenses(product_id);

-- ============================================================
-- TABLE: daily_usage
-- Tracks daily run limits per client
-- ============================================================
CREATE TABLE IF NOT EXISTS daily_usage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Counts
    pipeline_runs INTEGER DEFAULT 0,
    agent_sessions INTEGER DEFAULT 0,
    total_api_calls INTEGER DEFAULT 0,
    
    UNIQUE(client_id, date)
);

CREATE INDEX IF NOT EXISTS idx_daily_usage_client ON daily_usage(client_id, date DESC);

-- ============================================================
-- TABLE: feedback
-- Stores user feedback on pipeline outputs
-- ============================================================
CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id VARCHAR(255) NOT NULL,
    client_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Feedback
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    helpful BOOLEAN,
    improved_answer BOOLEAN,
    comments TEXT,
    
    -- Which layer was most useful
    most_useful_layer VARCHAR(10)
);

CREATE INDEX IF NOT EXISTS idx_feedback_run ON feedback(run_id);

-- ============================================================
-- FUNCTION: increment_license_usage
-- Called when a license is verified
-- ============================================================
CREATE OR REPLACE FUNCTION increment_license_usage(p_license_key VARCHAR)
RETURNS VOID AS $$
DECLARE
    v_uses INTEGER;
    v_max INTEGER;
    v_expires TIMESTAMP;
BEGIN
    SELECT uses_count, max_uses, expires_at INTO v_uses, v_max, v_expires
    FROM gumroad_licenses
    WHERE license_key = p_license_key;
    
    IF v_max IS NOT NULL AND v_uses >= v_max THEN
        RAISE EXCEPTION 'License usage limit exceeded';
    END IF;
    
    IF v_expires IS NOT NULL AND v_expires < NOW() THEN
        RAISE EXCEPTION 'License has expired';
    END IF;
    
    UPDATE gumroad_licenses
    SET uses_count = uses_count + 1,
        last_verified_at = NOW()
    WHERE license_key = p_license_key;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- FUNCTION: get_or_create_daily_usage
-- Gets or creates daily usage record
-- ============================================================
CREATE OR REPLACE FUNCTION get_or_create_daily_usage(p_client_id VARCHAR)
RETURNS daily_usage AS $$
DECLARE
    v_record daily_usage;
BEGIN
    SELECT * INTO v_record
    FROM daily_usage
    WHERE client_id = p_client_id AND date = CURRENT_DATE;
    
    IF v_record IS NULL THEN
        INSERT INTO daily_usage (client_id, date)
        VALUES (p_client_id, CURRENT_DATE)
        RETURNING * INTO v_record;
    END IF;
    
    RETURN v_record;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- FUNCTION: increment_daily_pipeline_runs
-- Increments pipeline run count
-- ============================================================
CREATE OR REPLACE FUNCTION increment_daily_pipeline_runs(p_client_id VARCHAR)
RETURNS VOID AS $$
BEGIN
    INSERT INTO daily_usage (client_id, date, pipeline_runs)
    VALUES (p_client_id, CURRENT_DATE, 1)
    ON CONFLICT (client_id, date)
    DO UPDATE SET pipeline_runs = daily_usage.pipeline_runs + 1;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================

ALTER TABLE pipeline_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE layer_outputs ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subtasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE gumroad_licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE daily_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only see their own data
CREATE POLICY "Users see own pipeline runs"
    ON pipeline_runs FOR SELECT
    USING (client_id = auth.uid() OR client_id IS NULL);

CREATE POLICY "Users insert own pipeline runs"
    ON pipeline_runs FOR INSERT
    WITH CHECK (client_id = auth.uid() OR client_id IS NULL);

CREATE POLICY "Users see own layer outputs"
    ON layer_outputs FOR SELECT
    USING (run_id IN (SELECT run_id FROM pipeline_runs WHERE client_id = auth.uid() OR client_id IS NULL));

CREATE POLICY "Users insert own layer outputs"
    ON layer_outputs FOR INSERT
    WITH CHECK (run_id IN (SELECT run_id FROM pipeline_runs WHERE client_id = auth.uid() OR client_id IS NULL));

CREATE POLICY "Users see own sessions"
    ON agent_sessions FOR SELECT
    USING (client_id = auth.uid() OR client_id IS NULL);

CREATE POLICY "Users manage own sessions"
    ON agent_sessions FOR ALL
    USING (client_id = auth.uid() OR client_id IS NULL);

CREATE POLICY "Users manage own subtasks"
    ON subtasks FOR ALL
    USING (session_id IN (SELECT session_id FROM agent_sessions WHERE client_id = auth.uid() OR client_id IS NULL));

-- Licenses: Anyone can verify, only service role can manage
CREATE POLICY "Anyone can verify license"
    ON gumroad_licenses FOR SELECT
    USING (TRUE);

-- Usage: Users see own usage
CREATE POLICY "Users see own usage"
    ON daily_usage FOR SELECT
    USING (client_id = auth.uid() OR client_id IS NULL);

-- Feedback: Anyone can insert, verify with run_id
CREATE POLICY "Anyone can submit feedback"
    ON feedback FOR INSERT
    WITH CHECK (TRUE);

CREATE POLICY "Users see own feedback"
    ON feedback FOR SELECT
    USING (client_id = auth.uid() OR client_id IS NULL);

-- ============================================================
-- VIEWS
-- ============================================================

-- View: Recent pipeline activity
CREATE OR REPLACE VIEW recent_pipeline_activity AS
SELECT 
    pr.run_id,
    pr.client_id,
    pr.created_at,
    pr.score_before,
    pr.score_after,
    pr.score_delta,
    pr.status,
    pr.mode,
    pr.final_answer,
    lo.layer_outputs_count
FROM pipeline_runs pr
LEFT JOIN (
    SELECT run_id, COUNT(*) as layer_outputs_count
    FROM layer_outputs
    GROUP BY run_id
) lo ON pr.run_id = lo.run_id
ORDER BY pr.created_at DESC
LIMIT 100;

-- View: Daily statistics
CREATE OR REPLACE VIEW daily_statistics AS
SELECT 
    date,
    COUNT(*) as total_runs,
    AVG(score_delta) as avg_score_improvement,
    SUM(pipeline_runs) as total_pipeline_runs,
    SUM(agent_sessions) as total_agent_sessions
FROM daily_usage
GROUP BY date
ORDER BY date DESC
LIMIT 30;

-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE pipeline_runs IS 'Stores 16-layer deep pipeline execution records';
COMMENT ON TABLE layer_outputs IS 'Individual layer outputs from pipeline runs';
COMMENT ON TABLE agent_sessions IS 'Multi-agent orchestration sessions';
COMMENT ON TABLE subtasks IS 'Individual subtasks within agent sessions';
COMMENT ON TABLE gumroad_licenses IS 'Verified Gumroad license keys for usage tracking';
COMMENT ON TABLE daily_usage IS 'Daily usage metrics per client for rate limiting';
COMMENT ON TABLE feedback IS 'User feedback on pipeline outputs';
