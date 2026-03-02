# 🚀 AI Benchmark Report

**Generated:** 2026-03-02 06:46:17

⚠️ **23/24** tests passed | 🪙 **19,762** tokens | 💰 **$0.0210** | ⏱️ **47.8s** total | **2.0s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ⚠️ 11/12 | 7,541 | 1,183 | $0.0009 | 22.3s | 1.9s |
| **3-pro** | ✅ 12/12 | 9,354 | 1,684 | $0.0201 | 25.6s | 2.1s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000077 | $2.30 | $23.01 |
| **3-pro** | $0.001676 | $50.28 | $502.81 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $2.30
  3-pro         │ ███ $50.28

10K requests/day:
  3-flash       │ █ $23.01
  3-pro         │ ████████████████████████████████████████ $502.81
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 0/0 | $0.0000 | 1.67s |
| BM-01: Simple steps query | 3-pro | ✅ | 0/0 | $0.0000 | 2.65s |
| BM-02: Multi-turn conversation | 3-flash | ❌ | 0/0 | $0.0000 | 0.87s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 0/0 | $0.0000 | 1.60s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 0/0 | $0.0000 | 1.41s |
| BM-03: AI daily summary | 3-flash | ✅ | 695/43 | $0.0001 | 0.91s |
| BM-03: AI daily summary | 3-pro | ✅ | 695/33 | $0.0010 | 0.93s |
| BM-04: Meal import - banana | 3-flash | ✅ | 1786/380 | $0.0002 | 2.53s |
| BM-04: Meal import - banana | 3-pro | ✅ | 1786/311 | $0.0038 | 2.24s |
| BM-05: Meal import - complex | 3-flash | ✅ | 1813/605 | $0.0003 | 3.72s |
| BM-05: Meal import - complex | 3-pro | ✅ | 1813/595 | $0.0052 | 3.57s |
| BM-05: Meal import - complex | 3-pro | ✅ | 1813/591 | $0.0052 | 3.47s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3247/155 | $0.0003 | 2.62s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3247/154 | $0.0048 | 2.77s |
| BM-07: Polish language query | 3-flash | ✅ | 0/0 | $0.0000 | 1.27s |
| BM-07: Polish language query | 3-pro | ✅ | 0/0 | $0.0000 | 1.38s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 0/0 | $0.0000 | 1.60s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 0/0 | $0.0000 | 1.47s |
| BM-09: Multi-tool query | 3-flash | ✅ | 0/0 | $0.0000 | 1.93s |
| BM-09: Multi-tool query | 3-pro | ✅ | 0/0 | $0.0000 | 2.23s |
| BM-10: Weekly summary query | 3-flash | ✅ | 0/0 | $0.0000 | 1.58s |
| BM-10: Weekly summary query | 3-pro | ✅ | 0/0 | $0.0000 | 1.53s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 0/0 | $0.0000 | 1.97s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 0/0 | $0.0000 | 1.91s |

## ❌ Failed Tests

### BM-02 - 3-flash
**Test:** Multi-turn conversation

**Error:**
```
Judge score: 0.0, reason: The assistant claimed we didn't discuss sleep, even though sleep duration is available in the daily summary for today. It should have reported the sleep duration, and been contextually relevant to the follow-up question. The assistant should have also provided the sleep data in Polish since that is the language the user is using. This is critical error.
```

