import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import express from "express";
import { z } from "zod";
// ─── Config ──────────────────────────────────────────────────────────────────
const BASE_URL = process.env.HEALTH_ASSISTANT_URL ?? "http://localhost:8080";
const API_KEY = process.env.CLAUDE_API_KEY ?? "";
const PORT = parseInt(process.env.MCP_PORT ?? "3100", 10);
if (!API_KEY) {
    console.error("ERROR: CLAUDE_API_KEY environment variable is not set.");
    process.exit(1);
}
// ─── HTTP helpers ─────────────────────────────────────────────────────────────
async function healthGet(path) {
    const res = await fetch(`${BASE_URL}${path}`, {
        headers: { Authorization: `Bearer ${API_KEY}` },
    });
    if (!res.ok) {
        const body = await res.text();
        throw new Error(`Health Assistant API error ${res.status}: ${body}`);
    }
    return res.json();
}
/** Calls /v1/assistant/chat (SSE stream) and returns the accumulated text response. */
async function askAssistant(message, conversationId) {
    const body = { message };
    if (conversationId)
        body.conversationId = conversationId;
    const res = await fetch(`${BASE_URL}/v1/assistant/chat`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${API_KEY}`,
            "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
    });
    if (!res.ok) {
        const errBody = await res.text();
        throw new Error(`Assistant API error ${res.status}: ${errBody}`);
    }
    let text = "";
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    outer: while (true) {
        const { done, value } = await reader.read();
        if (done)
            break;
        const chunk = decoder.decode(value, { stream: true });
        for (const line of chunk.split("\n")) {
            if (!line.startsWith("data:"))
                continue;
            try {
                const event = JSON.parse(line.slice(5).trim());
                if (event.type === "content")
                    text += event.content ?? "";
                if (event.type === "error")
                    throw new Error(String(event.message ?? event));
                if (event.type === "done")
                    break outer;
            }
            catch (e) {
                if (e instanceof SyntaxError)
                    continue; // incomplete JSON chunk
                throw e;
            }
        }
    }
    return text;
}
// ─── MCP server factory ───────────────────────────────────────────────────────
// Created fresh per-request (stateless — no session management needed).
function buildServer() {
    const server = new McpServer({
        name: "health-assistant",
        version: "1.0.0",
    });
    // ── 1. Daily Summary ──────────────────────────────────────────────────────
    server.tool("health_daily_summary", "Complete health summary for a specific date: steps, distance, calories, active minutes, sleep, heart rate, meals, workouts, energy targets and macros.", { date: z.string().describe("Date YYYY-MM-DD. Use today's date for the current day.") }, async ({ date }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/daily-summaries/${date}`), null, 2) }],
    }));
    // ── 2. Summary Range ─────────────────────────────────────────────────────
    server.tool("health_summary_range", "Aggregated health summaries for a date range (e.g. last 7 days, current month). Good for trend overviews.", {
        from: z.string().describe("Start date YYYY-MM-DD"),
        to: z.string().describe("End date YYYY-MM-DD"),
    }, async ({ from, to }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/daily-summaries/range?from=${from}&to=${to}`), null, 2) }],
    }));
    // ── 3. Steps ─────────────────────────────────────────────────────────────
    server.tool("health_steps", "Step count and distance data. Provide either a single date or a from+to range.", {
        date: z.string().optional().describe("Single date YYYY-MM-DD"),
        from: z.string().optional().describe("Start date YYYY-MM-DD (range)"),
        to: z.string().optional().describe("End date YYYY-MM-DD (range)"),
    }, async ({ date, from, to }) => {
        const path = date
            ? `/v1/steps/daily/${date}`
            : `/v1/steps/daily/range?from=${from}&to=${to}`;
        return { content: [{ type: "text", text: JSON.stringify(await healthGet(path), null, 2) }] };
    });
    // ── 4. Workouts ───────────────────────────────────────────────────────────
    server.tool("health_workouts", "Strength training sessions with exercises, sets, reps, weights and total volume for a date range.", {
        from: z.string().describe("Start date YYYY-MM-DD"),
        to: z.string().describe("End date YYYY-MM-DD"),
    }, async ({ from, to }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/workouts?from=${from}&to=${to}`), null, 2) }],
    }));
    // ── 5. Meals ──────────────────────────────────────────────────────────────
    server.tool("health_meals", "Meals with calories and macronutrients (protein, fat, carbs) for a date range.", {
        from: z.string().describe("Start date YYYY-MM-DD"),
        to: z.string().describe("End date YYYY-MM-DD"),
    }, async ({ from, to }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/meals/range?from=${from}&to=${to}`), null, 2) }],
    }));
    // ── 6. Weight ─────────────────────────────────────────────────────────────
    server.tool("health_weight", "Weight measurements with BMI, body fat %, muscle mass %, BMR. Use mode='latest' for most recent reading or mode='range' with dates.", {
        mode: z.enum(["latest", "range"]).default("latest"),
        from: z.string().optional().describe("Start date YYYY-MM-DD (range mode only)"),
        to: z.string().optional().describe("End date YYYY-MM-DD (range mode only)"),
    }, async ({ mode, from, to }) => {
        const path = mode === "latest"
            ? "/v1/weight/latest"
            : `/v1/weight/range?from=${from}&to=${to}`;
        return { content: [{ type: "text", text: JSON.stringify(await healthGet(path), null, 2) }] };
    });
    // ── 7. Sleep ──────────────────────────────────────────────────────────────
    server.tool("health_sleep", "Sleep sessions with total duration, start and end times for a date range.", {
        from: z.string().describe("Start date YYYY-MM-DD"),
        to: z.string().describe("End date YYYY-MM-DD"),
    }, async ({ from, to }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/sleep/range?from=${from}&to=${to}`), null, 2) }],
    }));
    // ── 8. Heart Rate ─────────────────────────────────────────────────────────
    server.tool("health_heart_rate", "Heart rate data (average, min, max, resting BPM) for a date range.", {
        from: z.string().describe("Start date YYYY-MM-DD"),
        to: z.string().describe("End date YYYY-MM-DD"),
    }, async ({ from, to }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/heartrate/range?from=${from}&to=${to}`), null, 2) }],
    }));
    // ── 9. Medical Exams ──────────────────────────────────────────────────────
    server.tool("health_medical_exams", "List medical examinations. Optionally filter by specialty (e.g. HEMATOLOGY, CARDIOLOGY), date range, or show only abnormal results.", {
        specialty: z.string().optional().describe("Medical specialty code"),
        from: z.string().optional().describe("Start date YYYY-MM-DD"),
        to: z.string().optional().describe("End date YYYY-MM-DD"),
        abnormalOnly: z.boolean().optional().describe("Return only exams with at least one abnormal result"),
    }, async ({ specialty, from, to, abnormalOnly }) => {
        const params = new URLSearchParams();
        if (specialty)
            params.set("specialty", specialty);
        if (from)
            params.set("from", from);
        if (to)
            params.set("to", to);
        if (abnormalOnly)
            params.set("abnormalOnly", "true");
        const qs = params.toString();
        return { content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/medical-exams${qs ? "?" + qs : ""}`), null, 2) }] };
    });
    // ── 10. Medical Exam Detail ───────────────────────────────────────────────
    server.tool("health_medical_exam_detail", "Full details of a single medical examination including all individual lab result values and reference ranges.", { examId: z.string().uuid().describe("Examination UUID from health_medical_exams") }, async ({ examId }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/medical-exams/${examId}`), null, 2) }],
    }));
    // ── 11. Lab Marker Trend ──────────────────────────────────────────────────
    server.tool("health_marker_trend", "Historical trend for a specific lab marker across all examinations (e.g. TSH, CHOL_TOTAL, GLUCOSE_FASTING, HGB).", { markerCode: z.string().describe("Lab marker code, e.g. TSH, CHOL_TOTAL, GLUCOSE_FASTING") }, async ({ markerCode }) => ({
        content: [{ type: "text", text: JSON.stringify(await healthGet(`/v1/medical-exams/marker-trend?markerCode=${encodeURIComponent(markerCode)}`), null, 2) }],
    }));
    // ── 12. Ask Assistant (natural language + mutations) ──────────────────────
    server.tool("health_ask", [
        "Ask the health assistant anything in natural language (Polish or English) or issue a mutation command.",
        "Use this for: recording meals/workouts/sleep/weight, energy requirements, body measurements,",
        "health pillars overview, complex multi-metric queries, or anything not covered by specific tools.",
        "Examples: 'zapisz trening: przysiad 4x8 100kg', 'ile mam zjeść dziś białka?',",
        "'jak wyglądają moje wyniki morfologii na przestrzeni roku?'",
    ].join(" "), {
        message: z.string().max(4000).describe("Question or command in natural language"),
        conversationId: z.string().uuid().optional().describe("Continue a previous conversation (UUID from prior response)"),
    }, async ({ message, conversationId }) => {
        const text = await askAssistant(message, conversationId);
        return { content: [{ type: "text", text }] };
    });
    return server;
}
// ─── Express HTTP server ──────────────────────────────────────────────────────
const app = express();
app.use(express.json());
// Health check
app.get("/health", (_req, res) => {
    res.json({ status: "ok", tools: 12, baseUrl: BASE_URL });
});
// MCP endpoint — stateless: fresh server + transport per request
app.all("/mcp", async (req, res) => {
    const server = buildServer();
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => {
        transport.close().catch(() => undefined);
    });
    try {
        await server.connect(transport);
        await transport.handleRequest(req, res, req.body);
    }
    catch (err) {
        console.error("MCP request error:", err);
        if (!res.headersSent) {
            res.status(500).json({ error: "Internal MCP server error" });
        }
    }
});
app.listen(PORT, () => {
    console.log(`✅ Health Assistant MCP server listening on port ${PORT}`);
    console.log(`   MCP endpoint : http://localhost:${PORT}/mcp`);
    console.log(`   Health check : http://localhost:${PORT}/health`);
    console.log(`   Backend URL  : ${BASE_URL}`);
});
