# 🚀 AI Benchmark Report

**Generated:** 2026-03-16 07:07:51

⚠️ **18/19** tests passed | 🪙 **8,267** tokens | 💰 **$0.0062** | ⏱️ **32.6s** total | **1.7s** avg

## 📊 Model Comparison

| Model | Pass Rate | Input Tokens | Output Tokens | Cost | Total Time | Avg Time |
|-------|-----------|--------------|---------------|------|------------|----------|
| **3-flash** | ⚠️ 9/10 | 3,942 | 189 | $0.0004 | 15.1s | 1.5s |
| **3-pro** | ✅ 9/9 | 3,942 | 194 | $0.0059 | 17.5s | 1.9s |

## 💰 Monthly Cost Projections

Based on average cost per request, projected monthly costs at scale:

| Model | Per Request | 1K/day (30d) | 10K/day (30d) |
|-------|-------------|--------------|---------------|
| **3-flash** | $0.000035 | $1.06 | $10.57 |
| **3-pro** | $0.000655 | $19.66 | $196.58 |

### 📊 Cost Visualization (Monthly)

```
1K requests/day:
  3-flash       │ █ $1.06
  3-pro         │ ████ $19.66

10K requests/day:
  3-flash       │ ██ $10.57
  3-pro         │ ████████████████████████████████████████ $196.58
```

## 📋 Test Results

| Test | Model | Status | Tokens | Cost | Time |
|------|-------|--------|--------|------|------|
| BM-01: Simple steps query | 3-flash | ✅ | 0/0 | $0.0000 | 1.59s |
| BM-01: Simple steps query | 3-pro | ✅ | 0/0 | $0.0000 | 3.50s |
| BM-02: Multi-turn conversation | 3-flash | ❌ | 0/0 | $0.0000 | 0.75s |
| BM-02: Multi-turn conversation | 3-flash | ✅ | 0/0 | $0.0000 | 1.26s |
| BM-02: Multi-turn conversation | 3-pro | ✅ | 0/0 | $0.0000 | 1.58s |
| BM-03: AI daily summary | 3-flash | ✅ | 695/35 | $0.0001 | 0.84s |
| BM-03: AI daily summary | 3-pro | ✅ | 695/40 | $0.0011 | 0.98s |
| BM-06: Sleep import - screenshot | 3-flash | ✅ | 3247/154 | $0.0003 | 2.37s |
| BM-06: Sleep import - screenshot | 3-pro | ✅ | 3247/154 | $0.0048 | 2.71s |
| BM-07: Polish language query | 3-flash | ✅ | 0/0 | $0.0000 | 1.36s |
| BM-07: Polish language query | 3-pro | ✅ | 0/0 | $0.0000 | 1.53s |
| BM-08: Date recognition - yesterday | 3-flash | ✅ | 0/0 | $0.0000 | 1.62s |
| BM-08: Date recognition - yesterday | 3-pro | ✅ | 0/0 | $0.0000 | 1.66s |
| BM-09: Multi-tool query | 3-flash | ✅ | 0/0 | $0.0000 | 1.64s |
| BM-09: Multi-tool query | 3-pro | ✅ | 0/0 | $0.0000 | 1.89s |
| BM-10: Weekly summary query | 3-flash | ✅ | 0/0 | $0.0000 | 1.62s |
| BM-10: Weekly summary query | 3-pro | ✅ | 0/0 | $0.0000 | 1.54s |
| BM-11: Long conversation (6 turns) | 3-flash | ✅ | 0/0 | $0.0000 | 2.05s |
| BM-11: Long conversation (6 turns) | 3-pro | ✅ | 0/0 | $0.0000 | 2.10s |

## ❌ Failed Tests

### BM-02 - 3-flash
**Test:** Multi-turn conversation

**Error:**
```
Judge score: 0.0, reason: Assistant failed to answer a direct question about sleep duration when data was available. The response incorrectly claimed that sleep data was not discussed previously, and failed to provide the requested sleep duration in hours or minutes as requested.
```

