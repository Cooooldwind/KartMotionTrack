# 轨迹后处理系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将追踪系统改为"记录原始数据 + 离线后处理"模式，提高轨迹精度

**Architecture:** 
- **记录阶段**：只存储原始GPS和IMU数据到单独的JSON文件
- **处理阶段**：用户点击"生成轨迹"后，使用Kalman滤波和动态规划算法进行批量后处理
- **输出阶段**：生成100Hz精度的轨迹点，保存到处理后的文件中

**Tech Stack:** Kotlin, Coroutines, Kalman Filter, Madgwick Filter

---

## 文件结构

```
app/src/main/java/com/karttracker/
├── model/
│   ├── RawTrackData.kt          # 新增：原始轨迹数据（GPS+IMU）
│   ├── ProcessedTrack.kt        # 新增：处理后的轨迹数据
│   └── RawDataPoint.kt          # 新增：原始数据点
├── storage/
│   ├── RawDataWriter.kt         # 新增：写入原始数据
│   ├── RawDataReader.kt         # 新增：读取原始数据
│   ├── TrackFileManager.kt      # 修改：支持原始数据和处理后数据
│   └── TrackProcessor.kt       # 新增：轨迹后处理引擎
├── processing/
│   ├── KalmanGPSFilter.kt       # 新增：GPS Kalman滤波
│   └── AdaptiveInterpolation.kt # 新增：自适应插值算法
├── MainActivity.kt              # 修改：简化为仅显示原始数据统计
├── HistoryActivity.kt           # 修改：支持轨迹生成按钮
└── TrackDetailActivity.kt      # 修改：新增生成轨迹按钮

app/src/main/res/layout/
├── activity_track_detail.xml    # 修改：添加"生成轨迹"按钮
└── item_track.xml              # 修改：显示处理状态
```

---

## Task 1: 创建原始数据模型

**Files:**
- Create: `app/src/main/java/com/karttracker/model/RawDataPoint.kt`
- Create: `app/src/main/java/com/karttracker/model/RawTrackData.kt`
- Create: `app/src/main/java/com/karttracker/model/ProcessedTrack.kt`
- Modify: `app/src/main/java/com/karttracker/model/TrackData.kt`

---

## Task 2: 创建原始数据读写器

**Files:**
- Create: `app/src/main/java/com/karttracker/storage/RawDataWriter.kt`
- Create: `app/src/main/java/com/karttracker/storage/RawDataReader.kt`

---

## Task 3: 重构 KartTracker

**Files:**
- Modify: `app/src/main/java/com/karttracker/KartTracker.kt`

---

## Task 4: 创建GPS Kalman滤波器

**Files:**
- Create: `app/src/main/java/com/karttracker/processing/KalmanGPSFilter.kt`

---

## Task 5: 创建自适应插值算法

**Files:**
- Create: `app/src/main/java/com/karttracker/processing/AdaptiveInterpolation.kt`

---

## Task 6: 创建轨迹后处理引擎

**Files:**
- Create: `app/src/main/java/com/karttracker/storage/TrackProcessor.kt`

---

## Task 7: 更新 TrackFileManager

**Files:**
- Modify: `app/src/main/java/com/karttracker/storage/TrackFileManager.kt`

---

## Task 8: 更新 TrackDetailActivity UI

**Files:**
- Modify: `app/src/main/java/com/karttracker/TrackDetailActivity.kt`
- Modify: `app/src/main/res/layout/activity_track_detail.xml`
- Modify: `app/src/main/res/layout/item_track.xml`

---

## Task 9: 更新 MainActivity 简化显示

**Files:**
- Modify: `app/src/main/java/com/karttracker/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`

---

## Task 10: 最终测试和版本更新

- 测试完整流程
- 更新版本号到 v1.2
