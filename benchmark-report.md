# 🚀 AI Benchmark Report

**Generated:** 2026-01-29 08:56:11

✅ **22/22** tests passed | 🪙 **96,517** tokens | 💰 **$0.0700** | ⏱️ **34.8s** total | **1.6s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ✅ 11/11 | 46,671 | 1,440 | $0.0039 | 17.5s | 1.6s |
| **3-pro** | ✅ 11/11 | 46,929 | 1,477 | $0.0660 | 17.2s | 1.6s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000357 | $10.72 | $107.25 |
| **3-pro** | $0.006004 | $180.13 | $1801.26 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $10.72
  3-pro         │ ████ $180.13

10K requests/day:
  3-flash       │ ██ $107.25
  3-pro         │ ████████████████████████████████████████ $1801.26
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 5571/53 | $0.0004 | 1.30s |
| BM-01: Simple steps query | 3-pro | ✅ | 5571/61 | $0.0073 | 1.97s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 5674/41 | $0.0004 | 1.23s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 5767/88 | $0.0076 | 1.23s |
| BM-03: AI daily summary | 3-flash | ✅ | 1300/33 | $0.0001 | 1.09s |
| BM-03: AI daily summary | 3-pro | ✅ | 1300/45 | $0.0019 | 1.04s |
| BM-04: Meal import - banana | 3-flash | ✅ | 2265/263 | $0.0002 | 2.70s |
| BM-04: Meal import - banana | 3-pro | ✅ | 2265/195 | $0.0038 | 1.93s |
| BM-05: Meal import - complex | 3-flash | ✅ | 2292/325 | $0.0003 | 2.67s |
| BM-05: Meal import - complex | 3-pro | ✅ | 2292/347 | $0.0046 | 2.79s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3944/155 | $0.0003 | 2.63s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3944/155 | $0.0057 | 2.42s |
| BM-07: Polish language query | 3-flash | ✅ | 5571/64 | $0.0004 | 1.30s |
| BM-07: Polish language query | 3-pro | ✅ | 5571/54 | $0.0072 | 1.32s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 5649/88 | $0.0005 | 1.16s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 5649/77 | $0.0074 | 1.37s |
| BM-09: Multi-tool query | 3-flash | ✅ | 5598/174 | $0.0005 | 1.58s |
| BM-09: Multi-tool query | 3-pro | ✅ | 5598/147 | $0.0077 | 1.35s |
| BM-10: Weekly summary query | 3-flash | ✅ | 5771/78 | $0.0005 | 1.31s |
| BM-10: Weekly summary query | 3-pro | ✅ | 5771/117 | $0.0078 | 1.19s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 3036/166 | $0.0003 | 0.57s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 3201/191 | $0.0050 | 0.63s |
