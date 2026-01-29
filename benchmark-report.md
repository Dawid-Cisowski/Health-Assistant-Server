# 🚀 AI Benchmark Report

**Generated:** 2026-01-29 09:45:39

✅ **22/22** tests passed | 🪙 **90,607** tokens | 💰 **$0.0652** | ⏱️ **32.6s** total | **1.5s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ✅ 11/11 | 43,991 | 1,449 | $0.0037 | 15.6s | 1.4s |
| **3-pro** | ✅ 11/11 | 43,830 | 1,337 | $0.0615 | 17.1s | 1.6s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000339 | $10.18 | $101.84 |
| **3-pro** | $0.005588 | $167.65 | $1676.52 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $10.18
  3-pro         │ ████ $167.65

10K requests/day:
  3-flash       │ ██ $101.84
  3-pro         │ ████████████████████████████████████████ $1676.52
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 5571/55 | $0.0004 | 1.42s |
| BM-01: Simple steps query | 3-pro | ✅ | 5571/65 | $0.0073 | 2.40s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 2811/31 | $0.0002 | 0.58s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 2813/77 | $0.0039 | 0.72s |
| BM-03: AI daily summary | 3-flash | ✅ | 1300/24 | $0.0001 | 0.72s |
| BM-03: AI daily summary | 3-pro | ✅ | 1300/34 | $0.0018 | 0.83s |
| BM-04: Meal import - banana | 3-flash | ✅ | 2265/238 | $0.0002 | 1.89s |
| BM-04: Meal import - banana | 3-pro | ✅ | 2265/214 | $0.0039 | 1.94s |
| BM-05: Meal import - complex | 3-flash | ✅ | 2292/350 | $0.0003 | 2.75s |
| BM-05: Meal import - complex | 3-pro | ✅ | 2292/358 | $0.0047 | 2.81s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3944/154 | $0.0003 | 2.52s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3944/155 | $0.0057 | 2.63s |
| BM-07: Polish language query | 3-flash | ✅ | 5571/55 | $0.0004 | 1.22s |
| BM-07: Polish language query | 3-pro | ✅ | 5571/52 | $0.0072 | 1.27s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 5649/79 | $0.0004 | 1.19s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 5649/83 | $0.0075 | 1.20s |
| BM-09: Multi-tool query | 3-flash | ✅ | 5598/151 | $0.0005 | 1.25s |
| BM-09: Multi-tool query | 3-pro | ✅ | 5598/133 | $0.0077 | 1.23s |
| BM-10: Weekly summary query | 3-flash | ✅ | 5771/111 | $0.0005 | 1.37s |
| BM-10: Weekly summary query | 3-pro | ✅ | 5771/123 | $0.0078 | 1.42s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 3219/201 | $0.0003 | 0.66s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 3056/43 | $0.0040 | 0.64s |
