# CleanGuard V0.2 Performance Report

Status: **Pending R3**

R0 establishes the report structure only. Emulator timing is not accepted as physical-device performance evidence.

## Required devices

- At least one mainstream mid-range Android device.
- At least one flagship Android device.
- Preferably one Android 10-12 device.

## Required datasets

- 10,000 media items.
- 50,000 media items.
- Target stress case: 100,000 media items.

## Measurements

| Area | Metric | Device | Dataset | Result | Gate |
|---|---|---|---:|---:|---|
| Startup | Cold start | Pending | N/A | Pending | R3 |
| Startup | Warm start | Pending | N/A | Pending | R3 |
| Scan | First full scan | Pending | 10k/50k/100k | Pending | R3 |
| Scan | No-change incremental scan | Pending | 10k/50k/100k | Pending | R3 |
| Scan | 100 add / 100 modify / 100 delete | Pending | 50k+ | Pending | R3 |
| Memory | Peak PSS / RSS and GC | Pending | 100k target | Pending | R3 |
| UI | Home/results/history/duplicates scrolling | Pending | Large dataset | Pending | R3 |

## Tools

Use Android Macrobenchmark and Perfetto; use Android Studio Profiler only where additional diagnosis is necessary. Record traces and test conditions without collecting user media content.
