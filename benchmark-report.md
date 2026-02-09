# 🚀 AI Benchmark Report

**Generated:** 2026-02-09 06:56:39

⚠️ **21/22** tests passed | 🪙 **112,248** tokens | 💰 **$0.0771** | ⏱️ **38.3s** total | **1.7s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ✅ 11/11 | 56,658 | 1,271 | $0.0046 | 18.8s | 1.7s |
| **3-pro** | ⚠️ 10/11 | 53,101 | 1,218 | $0.0725 | 19.5s | 1.8s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000421 | $12.63 | $126.29 |
| **3-pro** | $0.006588 | $197.64 | $1976.35 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $12.63
  3-pro         │ ███ $197.64

10K requests/day:
  3-flash       │ ██ $126.29
  3-pro         │ ████████████████████████████████████████ $1976.35
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 6967/48 | $0.0005 | 1.42s |
| BM-01: Simple steps query | 3-pro | ✅ | 6967/69 | $0.0091 | 2.55s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 7040/27 | $0.0005 | 1.48s |
| BM-02: Multi-turn conversation | 3-pro | ❌ | 3536/19 | $0.0045 | 0.72s |
| BM-03: AI daily summary | 3-flash | ✅ | 1569/32 | $0.0001 | 0.74s |
| BM-03: AI daily summary | 3-pro | ✅ | 1569/19 | $0.0021 | 0.70s |
| BM-04: Meal import - banana | 3-flash | ✅ | 2534/214 | $0.0003 | 1.72s |
| BM-04: Meal import - banana | 3-pro | ✅ | 2534/218 | $0.0043 | 1.88s |
| BM-05: Meal import - complex | 3-flash | ✅ | 2561/355 | $0.0003 | 2.42s |
| BM-05: Meal import - complex | 3-pro | ✅ | 2561/325 | $0.0048 | 2.35s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 4213/154 | $0.0004 | 2.32s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 4213/155 | $0.0060 | 2.67s |
| BM-07: Polish language query | 3-flash | ✅ | 6967/45 | $0.0005 | 1.28s |
| BM-07: Polish language query | 3-pro | ✅ | 6967/41 | $0.0089 | 1.43s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 7045/75 | $0.0006 | 1.64s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 7045/74 | $0.0092 | 1.65s |
| BM-09: Multi-tool query | 3-flash | ✅ | 6994/54 | $0.0005 | 1.58s |
| BM-09: Multi-tool query | 3-pro | ✅ | 6994/156 | $0.0095 | 2.55s |
| BM-10: Weekly summary query | 3-flash | ✅ | 7055/80 | $0.0006 | 1.83s |
| BM-10: Weekly summary query | 3-pro | ✅ | 7055/65 | $0.0091 | 1.72s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 3713/187 | $0.0003 | 2.35s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 3660/77 | $0.0050 | 1.30s |

## ❌ Failed Tests

### BM-02 - 3-pro
**Test:** Multi-turn conversation

**Error:**
```
Judge score: 0.0, reason: The response says that there is no sleep data available for today, when in fact, the user has 7 hours of sleep recorded for today. The assistant should have provided this information instead of stating that there is no data.
```

