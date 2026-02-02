# 🚀 AI Benchmark Report

**Generated:** 2026-02-02 06:53:32

✅ **22/22** tests passed | 🪙 **99,243** tokens | 💰 **$0.0739** | ⏱️ **28.4s** total | **1.3s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ✅ 11/11 | 46,778 | 1,453 | $0.0039 | 14.1s | 1.3s |
| **3-pro** | ✅ 11/11 | 49,373 | 1,639 | $0.0699 | 14.3s | 1.3s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000359 | $10.76 | $107.57 |
| **3-pro** | $0.006356 | $190.67 | $1906.67 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $10.76
  3-pro         │ ████ $190.67

10K requests/day:
  3-flash       │ ██ $107.57
  3-pro         │ ████████████████████████████████████████ $1906.67
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 5571/70 | $0.0004 | 1.14s |
| BM-01: Simple steps query | 3-pro | ✅ | 5571/77 | $0.0073 | 1.82s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 5710/51 | $0.0004 | 1.11s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 5716/90 | $0.0076 | 0.89s |
| BM-03: AI daily summary | 3-flash | ✅ | 1300/44 | $0.0001 | 0.92s |
| BM-03: AI daily summary | 3-pro | ✅ | 1300/34 | $0.0018 | 0.85s |
| BM-04: Meal import - banana | 3-flash | ✅ | 2265/214 | $0.0002 | 1.56s |
| BM-04: Meal import - banana | 3-pro | ✅ | 2265/208 | $0.0039 | 1.72s |
| BM-05: Meal import - complex | 3-flash | ✅ | 2292/340 | $0.0003 | 2.89s |
| BM-05: Meal import - complex | 3-pro | ✅ | 2292/339 | $0.0046 | 2.31s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3944/155 | $0.0003 | 2.27s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3944/154 | $0.0057 | 2.59s |
| BM-07: Polish language query | 3-flash | ✅ | 5571/80 | $0.0004 | 0.52s |
| BM-07: Polish language query | 3-pro | ✅ | 5571/42 | $0.0072 | 1.04s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 5649/73 | $0.0004 | 1.04s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 5649/83 | $0.0075 | 1.05s |
| BM-09: Multi-tool query | 3-flash | ✅ | 5598/158 | $0.0005 | 0.99s |
| BM-09: Multi-tool query | 3-pro | ✅ | 5598/166 | $0.0078 | 1.07s |
| BM-10: Weekly summary query | 3-flash | ✅ | 5771/90 | $0.0005 | 1.09s |
| BM-10: Weekly summary query | 3-pro | ✅ | 5771/145 | $0.0079 | 0.51s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 3107/178 | $0.0003 | 0.55s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 5696/301 | $0.0086 | 0.48s |
