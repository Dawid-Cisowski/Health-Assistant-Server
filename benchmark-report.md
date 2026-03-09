# 🚀 AI Benchmark Report

**Generated:** 2026-03-09 06:50:41

⚠️ **22/24** tests passed | 🪙 **17,414** tokens | 💰 **$0.0161** | ⏱️ **46.0s** total | **1.9s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ⚠️ 11/13 | 7,541 | 1,178 | $0.0009 | 22.5s | 1.7s |
| **3-pro** | ✅ 11/11 | 7,541 | 1,154 | $0.0152 | 23.6s | 2.1s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000071 | $2.12 | $21.21 |
| **3-pro** | $0.001381 | $41.44 | $414.44 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $2.12
  3-pro         │ ███ $41.44

10K requests/day:
  3-flash       │ ██ $21.21
  3-pro         │ ████████████████████████████████████████ $414.44
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 0/0 | $0.0000 | 1.65s |
| BM-01: Simple steps query | 3-pro | ✅ | 0/0 | $0.0000 | 3.38s |
| BM-02: Multi-turn conversation | 3-flash | ❌ | 0/0 | $0.0000 | 0.68s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 0/0 | $0.0000 | 1.47s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 0/0 | $0.0000 | 1.42s |
| BM-03: AI daily summary | 3-flash | ✅ | 695/49 | $0.0001 | 0.98s |
| BM-03: AI daily summary | 3-pro | ✅ | 695/33 | $0.0010 | 0.77s |
| BM-04: Meal import - banana | 3-flash | ✅ | 1786/377 | $0.0002 | 3.12s |
| BM-04: Meal import - banana | 3-pro | ✅ | 1786/370 | $0.0041 | 3.14s |
| BM-05: Meal import - complex | 3-flash | ✅ | 1813/597 | $0.0003 | 4.15s |
| BM-05: Meal import - complex | 3-pro | ✅ | 1813/597 | $0.0053 | 3.91s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3247/155 | $0.0003 | 2.10s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3247/154 | $0.0048 | 2.52s |
| BM-07: Polish language query | 3-flash | ✅ | 0/0 | $0.0000 | 1.37s |
| BM-07: Polish language query | 3-pro | ✅ | 0/0 | $0.0000 | 1.36s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 0/0 | $0.0000 | 1.38s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 0/0 | $0.0000 | 1.48s |
| BM-09: Multi-tool query | 3-flash | ❌ | 0/0 | $0.0000 | 1.10s |
| BM-09: Multi-tool query | 3-flash | ✅ | 0/0 | $0.0000 | 0.97s |
| BM-09: Multi-tool query | 3-pro | ✅ | 0/0 | $0.0000 | 2.72s |
| BM-10: Weekly summary query | 3-flash | ✅ | 0/0 | $0.0000 | 1.64s |
| BM-10: Weekly summary query | 3-pro | ✅ | 0/0 | $0.0000 | 1.52s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 0/0 | $0.0000 | 1.87s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 0/0 | $0.0000 | 1.37s |

## ❌ Failed Tests

### BM-02 - 3-flash
**Test:** Multi-turn conversation

**Error:**
```
Judge score: 0.0, reason: Assistant should have provided sleep information as it was available, instead it said 'We didn't discuss sleep yet.'
```

### BM-09 - 3-flash
**Test:** Multi-tool query

**Error:**
```
Judge score: 0.0, reason: The response only says it will retrieve information but does not return any data
```

