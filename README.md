# KartMotionTrack - 卡丁车高频高精度轨迹追踪 APP

基于 Android 的高性能卡丁车轨迹追踪应用，使用多传感器融合技术和 GPS 插值算法实现 100Hz 高频轨迹记录。

## 📦 下载安装

### 最新版本 (v1.3)
[![GitHub Actions](https://img.shields.io/github/actions/workflow/status/Cooooldwind/KartMotionTrack/android-ci.yml?branch=main&label=Build&style=flat-square)](https://github.com/Cooooldwind/KartMotionTrack/actions)

- **[下载 Debug APK (最新构建)](https://github.com/Cooooldwind/KartMotionTrack/actions)**
  - 点击最新运行的 workflow
  - 在 Artifacts 下载 `app-debug-apk-xxx`
- **Release APK (仅 main 分支)** - 同样在 Actions 下载

## 🎯 功能特性

- **100Hz 高频采样** - 传感器 100Hz 采样
- **GPS 线性插值** - 1Hz GPS 信号插值为 100Hz 轨迹点
- **原始数据记录** - 先记录后处理，支持离线轨迹生成
- **GPS Kalman 滤波** - 卡尔曼滤波平滑 GPS 信号
- **自适应插值** - 根据传感器数据自动调整轨迹偏移
- **轨迹可视化** - Canvas 绘制 2D 轨迹预览
- **多格式导出** - 支持 GPX/CSV 格式导出
- **简洁 UI** - 实时显示记录状态和统计信息

## 📱 技术架构

```
KartMotionTrack/
├── app/
│   ├── src/main/java/com/karttracker/
│   │   ├── model/              # 数据模型
│   │   │   ├── RawDataPoint.kt      # 原始数据点
│   │   │   ├── ProcessedTrack.kt    # 处理后轨迹
│   │   │   └── TrackPoint.kt       # 轨迹点
│   │   ├── sensors/            # 传感器采集
│   │   │   ├── IMUSampler.kt        # IMU 采样
│   │   │   └── GPSCollector.kt      # GPS 采集
│   │   ├── processing/         # 信号处理
│   │   │   ├── KalmanGPSFilter.kt   # GPS 卡尔曼滤波
│   │   │   └── AdaptiveInterpolation.kt # 自适应插值
│   │   ├── storage/            # 数据存储
│   │   │   ├── RawDataWriter.kt     # 原始数据写入
│   │   │   ├── RawDataReader.kt     # 原始数据读取
│   │   │   └── TrackProcessor.kt    # 轨迹后处理
│   │   ├── KartTracker.kt      # 主控制器
│   │   ├── MainActivity.kt     # 主界面
│   │   ├── HistoryActivity.kt  # 历史记录
│   │   ├── TrackDetailActivity.kt # 轨迹详情 + 可视化
│   │   └── TrackVisualizationView.kt # 轨迹自定义绘制
│   └── res/                    # 资源文件
└── docs/                       # 设计文档
```

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK 26 或更高
- JDK 17 或更高
- 真实 Android 设备（需要加速度计、陀螺仪、GPS）

### 编译安装

1. 用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备（需开启 USB 调试）
4. 点击 Run 按钮或使用命令：
   ```bash
   ./gradlew installDebug
   ```

### 使用说明

1. **启动应用** - 在设备上打开 KartMotionTrack
2. **授权权限** - 授予位置和传感器权限
3. **开始追踪** - 点击"开始追踪"按钮
4. **停止追踪** - 点击"停止追踪"按钮
5. **查看历史** - 点击"历史轨迹"查看所有记录
6. **生成轨迹** - 点击"生成轨迹"进行离线处理
7. **查看轨迹** - 在详情页查看 2D 轨迹预览
8. **导出数据** - 处理后可导出 GPX 或 CSV 格式

## 📊 数据格式

### 原始数据 (raw_track_*.json)

JSONL 格式，每行一个数据点：

```json
{"timestamp":1234567890.123,"type":"GPS","gpsLat":30.123456789,"gpsLon":120.123456789,"gpsAlt":50.5,"gpsSpeed":30.2,"gpsBearing":180.0,"gpsAccuracy":5.0}
{"timestamp":1234567890.123,"type":"IMU","accelX":0.1,"accelY":0.2,"accelZ":9.8,"gyroX":0.01,"gyroY":0.02,"gyroZ":0.03}
```

### 处理后轨迹 (processed_*.json)

```json
{"timestamp":1234567890.000,"lat":30.123456789,"lon":120.123456789,"alt":50.5,"speed":30.2,"accuracy":2.0,"roll":0.1,"pitch":0.2,"yaw":180.0}
```

## 🔧 核心算法

### GPS Kalman 滤波

- 一维卡尔曼滤波分别处理经纬度
- 根据 GPS 精度动态调整滤波权重
- 消除 GPS 信号抖动

### 自适应插值

- 线性插值生成 100Hz 轨迹点
- 基于加速度计算横向偏移
- 限制最大偏移量 ±2 米
- 支持急转弯检测和修正

### 轨迹可视化

- Canvas 自定义 2D 绘制
- 自动缩放和平移
- 起点（绿色）/ 终点（红色）标记
- 网格背景辅助查看

## 📝 开发计划

- [x] 基础项目结构
- [x] 传感器采集模块
- [x] 原始数据记录
- [x] 轨迹后处理引擎
- [x] GPS Kalman 滤波
- [x] 自适应插值算法
- [x] 数据导出（GPX、CSV）
- [x] 轨迹可视化
- [ ] 赛道分析功能
- [ ] 云端同步

## 📄 许可证

MIT License - 详见 LICENSE 文件
