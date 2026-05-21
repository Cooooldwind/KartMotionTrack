# 卡丁车高频高精度轨迹追踪 APP

一个基于 Android 的高性能卡丁车轨迹追踪应用，使用多传感器融合技术实现 200Hz 高频、分米级精度的轨迹记录。

## 🎯 功能特性

- **200Hz 高频采样** - 加速度计和陀螺仪 200Hz 采样
- **振动滤波** - 自适应三级滤波，消除卡丁车振动干扰
- **姿态跟踪** - Madgwick 滤波实时跟踪手机 3D 姿态
- **坐标系转换** - 自动把手机坐标转换为地面坐标，支持手机任意放置
- **传感器融合** - EKF 扩展卡尔曼滤波融合 IMU 和 GPS
- **批量存储** - 环形缓冲 + 批量 JSON 写入，保证性能
- **简洁 UI** - 实时显示位置、速度、姿态信息

## 📱 技术架构

```
KartMotionTrack/
├── app/
│   ├── src/main/java/com/karttracker/
│   │   ├── model/              # 数据模型
│   │   │   ├── IMUData.kt
│   │   │   ├── GPSData.kt
│   │   │   ├── TrackPoint.kt
│   │   │   ├── TrackState.kt
│   │   │   └── FilteredIMUData.kt
│   │   ├── sensors/            # 传感器采集
│   │   │   ├── IMUSampler.kt
│   │   │   ├── GPSCollector.kt
│   │   │   └── SensorCalibrator.kt
│   │   ├── processing/         # 信号处理
│   │   │   ├── VibrationFilter.kt
│   │   │   ├── LowPassFilter.kt
│   │   │   ├── MedianFilter.kt
│   │   │   ├── KalmanFilter1D.kt
│   │   │   └── GravityExtractor.kt
│   │   ├── fusion/             # 传感器融合
│   │   │   ├── MadgwickFilter.kt
│   │   │   ├── EKF.kt
│   │   │   └── CoordinateTransformer.kt
│   │   ├── storage/            # 数据存储
│   │   │   ├── CircularBuffer.kt
│   │   │   └── BatchJsonWriter.kt
│   │   ├── KartTracker.kt      # 主控制器
│   │   └── MainActivity.kt     # UI
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
2. **授权权限** - 授予位置和存储权限
3. **等待 GPS 定位** - 初次使用需等待 GPS 信号
4. **放置手机** - 手机可任意放置（裤兜、支架均可）
5. **开始追踪** - 点击"开始"按钮
6. **结束追踪** - 点击"停止"按钮
7. **查看数据** - 轨迹数据存储在：
   ```
   /Android/data/com.karttracker/files/
   ```

## 📊 数据格式

轨迹数据保存为 JSONL (JSON Lines) 格式，每行一个轨迹点：

```json
{
  "timestamp": 1234567890.123,
  "lat": 30.123456789,
  "lon": 120.123456789,
  "alt": 50.5,
  "speed": 30.2,
  "accuracy": 0.5,
  "roll": 0.1,
  "pitch": 0.2,
  "yaw": 180.0
}
```

## 🔧 核心算法

### 振动滤波
- 振动强度检测（加速度方差）
- 自适应三级滤波（低通 + 中值 + 卡尔曼）

### Madgwick 姿态滤波
- 四元数表示姿态
- 陀螺仪积分预测
- 重力 + 磁力计修正

### EKF 传感器融合
- 11维状态向量（位置、速度、姿态、零偏）
- IMU 高频预测 (200Hz)
- GPS 低频修正 (1-5Hz)

## 📝 开发计划

- [x] 基础项目结构
- [x] 传感器采集模块
- [x] 信号处理模块
- [x] 姿态跟踪模块
- [x] EKF 传感器融合
- [x] 数据存储模块
- [x] UI 界面
- [ ] 轨迹可视化
- [ ] 数据导出（GPX、CSV）
- [ ] 赛道分析功能

## 📄 许可证

MIT License - 详见 LICENSE 文件
