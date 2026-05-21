# GPS点之间的传感器辅助插值方案 - 技术说明

**版本**：1.0  
**日期**：2026-05-21  
**作者**：KartTracker开发团队

---

## 1. 系统架构概述

### 1.1 数据采集层

```
┌─────────────────────────────────────────────────────┐
│                   数据采集层                         │
├─────────────────────────────────────────────────────┤
│  GPS 接收器              │  IMU 传感器组            │
│  - 采样率：1Hz           │  - 采样率：100Hz        │
│  - 经度、纬度、高度       │  - 加速度计（XYZ）       │
│  - 速度、航向角           │  - 陀螺仪（XYZ）        │
│  - 精度指标              │  - 磁力计（XYZ）        │
└─────────────────────────────────────────────────────┘
                           │
                           ↓
┌─────────────────────────────────────────────────────┐
│                   插值处理层                         │
├─────────────────────────────────────────────────────┤
│  输入：                                            │
│  - GPS点A: (lat₁, lon₁, alt₁, t₁)                 │
│  - GPS点B: (lat₂, lon₂, alt₂, t₂)                 │
│  - IMU缓存：姿态数据队列                           │
│                                                     │
│  输出：                                            │
│  - 100个插值点，每个间隔0.01秒                     │
└─────────────────────────────────────────────────────┘
```

### 1.2 GPS采样特性

| 参数 | 数值 | 说明 |
|------|------|------|
| 采样率 | 1 Hz | 每秒1个数据点 |
| 更新时间 | 1秒 | GPS接收器固有特性 |
| 民用精度 | 3-5米 | 开放天空下 |
| 城市精度 | 5-15米 | 有遮挡环境 |
| 首次定位时间 | 15-30秒 | 冷启动 |

---

## 2. 核心公式

### 2.1 线性插值公式

对于任意时刻 $t$（其中 $t_1 \leq t \leq t_2$），位置插值：

$$\text{lat}(t) = \text{lat}_1 + (\text{lat}_2 - \text{lat}_1) \times \frac{t - t_1}{t_2 - t_1}$$

$$\text{lon}(t) = \text{lon}_1 + (\text{lon}_2 - \text{lon}_1) \times \frac{t - t_1}{t_2 - t_1}$$

$$\text{alt}(t) = \text{alt}_1 + (\text{alt}_2 - \text{alt}_1) \times \frac{t - t_1}{t_2 - t_1}$$

其中：
- $(\text{lat}_1, \text{lon}_1, \text{alt}_1)$：起始GPS点
- $(\text{lat}_2, \text{lon}_2, \text{alt}_2)$：结束GPS点
- $t_1, t_2$：对应时间戳
- $t$：插值时刻

**简化表示**：
```
progress = (t - t₁) / (t₂ - t₁)
lat(t) = lat₁ + (lat₂ - lat₁) × progress
lon(t) = lon₁ + (lon₂ - lon₁) × progress
```

### 2.2 速度插值

$$\text{speed}(t) = \text{speed}_1 + (\text{speed}_2 - \text{speed}_1) \times \frac{t - t_1}{t_2 - t_1}$$

### 2.3 精度插值（线性衰减）

$$\text{accuracy}(t) = \text{accuracy}_1 \times (1 - p) + \text{accuracy}_2 \times p$$

其中 $p = \frac{t - t_1}{t_2 - t_1}$ 为进度百分比

---

## 3. 姿态计算（Madgwick滤波）

### 3.1 四元数表示

姿态用四元数表示：

$$q = [w, x, y, z]$$

其中 $|q| = 1$（归一化）

### 3.2 Madgwick算法更新

每收到一组IMU数据，四元数按以下公式更新：

$$q_{\text{new}} = q_{\text{old}} + \dot{q} \cdot \Delta t$$

其中 $\dot{q}$ 是四元数变化率：

$$\dot{q} = \frac{1}{2} \begin{bmatrix} 0 & -\omega_x & -\omega_y & -\omega_z \\ \omega_x & 0 & \omega_z & -\omega_y \\ \omega_y & -\omega_z & 0 & \omega_x \\ \omega_z & \omega_y & -\omega_x & 0 \end{bmatrix} q_{\text{old}} - \beta \cdot \nabla q$$

其中：
- $\omega = [\omega_x, \omega_y, \omega_z]$：陀螺仪角速度（rad/s）
- $\Delta t$：采样间隔（约0.01秒）
- $\beta$：Madgwick增益（默认0.1）
- $\nabla q$：梯度下降修正项（由加速度计和磁力计计算）

### 3.3 四元数到欧拉角转换

从四元数转换为欧拉角（弧度）：

$$\text{roll} = \arctan\frac{2(wx + yz)}{1 - 2(x^2 + y^2)}$$

$$\text{pitch} = \arcsin(2(wy - zx))$$

$$\text{yaw} = \arctan\frac{2(wz + xy)}{1 - 2(y^2 + z^2)}$$

---

## 4. 插值流程详解

### 4.1 时间线示意

```
时间线：
t₁ ─────── t₁+0.01 ─────── t₁+0.02 ─────── ... ─────── t₂
│            │               │               │               │
↓            ↓               ↓               ↓               ↓
GPS A      插值点1        插值点2        插值点99       GPS B
(lat₁,lon₁)               lat₂,lon₂)
```

### 4.2 具体步骤

#### 步骤1：收到GPS点A（t = t₁）
- 保存为起始点
- 直接输出第一个轨迹点
- 代码：
```kotlin
val trackPoint = TrackPoint(
    timestamp = gpsPoint.timestamp,
    lat = gpsPoint.lat,
    lon = gpsPoint.lon,
    alt = gpsPoint.alt,
    speed = gpsPoint.speed,
    accuracy = gpsPoint.accuracy,
    roll = 0f,
    pitch = 0f,
    yaw = 0f
)
```

#### 步骤2：收到GPS点B（t = t₂）
- 计算时间差：$\Delta t = t_2 - t_1$
- 计算插值点数：$N = \Delta t \times 100$（向下取整，至少2个点）
- 代码：
```kotlin
val timeDiff = end.timestamp - start.timestamp
val targetPointCount = (timeDiff * 100).toInt().coerceAtLeast(2)
```

#### 步骤3：生成插值点（i = 0 到 N-1）
对于每个点 i：
- 计算进度：$p = \frac{i}{N-1}$
- 计算时间戳：$t = t_1 + p \times \Delta t$
- 计算位置：使用线性插值公式
- 获取姿态：从Madgwick滤波器获取当前姿态
- 生成轨迹点并保存

代码：
```kotlin
for (i in 0 until targetPointCount) {
    val progress = i.toFloat() / (targetPointCount - 1)
    
    val lat = lerp(start.lat, end.lat, progress)
    val lon = lerp(start.lon, end.lon, progress)
    val alt = lerp(start.alt, end.alt, progress)
    val speed = lerp(start.speed, end.speed, progress)
    
    val (roll, pitch, yaw) = madgwickFilter.getEulerAngles()
    
    val timestamp = start.timestamp + progress * timeDiff
    
    val trackPoint = TrackPoint(
        timestamp = timestamp,
        lat = lat,
        lon = lon,
        alt = alt,
        speed = speed,
        accuracy = start.accuracy * (1 - progress) + end.accuracy * progress,
        roll = roll,
        pitch = pitch,
        yaw = yaw
    )
}
```

---

## 5. 传感器数据的作用

### 5.1 GPS数据
| 作用 | 说明 |
|------|------|
| 绝对位置 | 确定起点和终点的真实地理坐标 |
| 速度参考 | 用于速度插值 |
| 精度指标 | 标记数据质量 |
| 航向参考 | 可用于航向角校准 |

### 5.2 IMU数据
| 作用 | 说明 |
|------|------|
| 姿态跟踪 | 计算横滚、俯仰、航向角 |
| 加速度 | 用于姿态估计（Madgwick算法） |
| 角速度 | 用于姿态预测（Madgwick算法） |
| 磁力计 | 用于航向角校准 |

### 5.3 传感器数据影响分析

#### GPS对轨迹的影响
- **直接影响**：位置、高度、速度、精度
- **插值方式**：线性插值，100个点均匀分布
- **精度保证**：起点和终点100%准确

#### IMU对轨迹的影响
- **直接影响**：姿态角（roll、pitch、yaw）
- **间接影响**：无（当前实现中位置不依赖IMU）
- **用途**：记录车辆倾斜角度，可用于G力分析

---

## 6. 当前实现的简化

⚠️ **重要说明**：当前实现中，**位置完全由GPS线性插值决定**，IMU数据**仅用于姿态计算**。

这是一个简化方案，好处是：
- ✅ 绝对不会有位置漂移
- ✅ 起点和终点100%准确
- ✅ 算法简单稳定
- ✅ 计算量小，省电

**潜在问题**：
- ❌ 假设两GPS点之间是直线运动
- ❌ 无法检测GPS采样间隔内的急转弯
- ❌ 中间点精度完全依赖GPS精度

---

## 7. 更高级的方案（可选）

### 7.1 基于速度的插值

利用GPS速度计算中间点：

$$v(t) = \frac{\text{distance}(A, B)}{t_2 - t_1}$$

对于每个中间点，从A点开始累积距离：

$$\text{lat}_i = \text{lat}_A + \frac{\sum v \cdot \Delta t}{111111} \times \cos(\text{bearing})$$

### 7.2 基于IMU的曲线拟合

利用加速度积分修正轨迹：

1. 从GPS得到A、B两点
2. 从IMU得到加速度序列
3. 用加速度积分得到速度变化
4. 用速度变化调整曲线形状

$$\text{lat}_{\text{adjusted}} = \text{lat}_{\text{linear}} + \int\int a_x \, dt^2$$

### 7.3 GPS延迟补偿

考虑到GPS有固有延迟，可以对起点和终点进行预测：

$$\text{lat}_{\text{predicted}} = \text{lat}_{\text{GPS}} + v \times \Delta t_{\text{delay}}$$

---

## 8. 性能指标

| 指标 | 数值 | 说明 |
|------|------|------|
| GPS采样率 | 1 Hz | GPS接收器更新频率 |
| IMU采样率 | 100 Hz | 传感器更新频率 |
| 输出轨迹频率 | 100 Hz | 最终轨迹点频率 |
| 位置精度 | GPS精度 | 取决于GPS接收器 |
| 姿态精度 | 约1° | Madgwick滤波精度 |
| 数据量 | 360,000点/小时 | 100Hz × 3600秒 |
| 存储空间 | ~50MB/小时 | 每点约150字节 |

---

## 9. 优缺点分析

### ✅ 优点
1. **位置绝对准确** - GPS保证，无漂移
2. **算法简单稳定** - 线性插值，计算量小
3. **可靠性高** - 不依赖传感器精度
4. **省电** - 计算负载低
5. **调试方便** - 易于理解和修改

### ❌ 缺点
1. **中间点精度受限** - 完全依赖GPS精度
2. **直线运动假设** - 无法检测急转弯
3. **无法利用IMU优势** - 高频特性未充分利用
4. **卡丁车场景局限** - 频繁加减速、转弯

---

## 10. 卡丁车场景的特殊考虑

### 10.1 卡丁车运动特点
- **速度范围**：0-120 km/h
- **加速度**：高（急加速、急刹车）
- **转弯**：频繁且急
- **震动**：发动机震动大

### 10.2 当前方案在卡丁车场景的表现

| 场景 | 表现 | 说明 |
|------|------|------|
| 直道加速 | ⭐⭐⭐⭐⭐ | 线性插值准确 |
| 急转弯 | ⭐⭐ | 可能检测不到 |
| 急刹车 | ⭐⭐ | 速度变化可能不平滑 |
| 原地绕圈 | ⭐ | 轨迹可能呈多边形 |

### 10.3 建议的改进方向

1. **提高GPS采样率**
   - 使用双频GPS（如果手机支持）
   - 或使用RTK差分定位

2. **利用IMU进行曲线拟合**
   - 在GPS点之间用加速度积分
   - 检测急转弯并修正轨迹

3. **地图匹配**
   - 如果有赛道地图
   - 将轨迹匹配到地图上

---

## 11. 代码实现

### 11.1 核心类结构

```
KartTracker/
├── KartTracker.kt          # 主控制器
├── sensors/
│   ├── IMUSampler.kt       # IMU采样（100Hz）
│   └── GPSCollector.kt     # GPS采集（1Hz）
├── fusion/
│   └── MadgwickFilter.kt   # 姿态融合
└── storage/
    └── BatchJsonWriter.kt  # 数据存储
```

### 11.2 关键代码片段

#### GPS点数据结构
```kotlin
private data class GPSPoint(
    val timestamp: Double,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float
)
```

#### 线性插值函数
```kotlin
private fun lerp(a: Double, b: Double, t: Float): Double {
    return a + (b - a) * t
}
```

#### 插值生成
```kotlin
for (i in 0 until targetPointCount) {
    val progress = i.toFloat() / (targetPointCount - 1)
    val lat = lerp(start.lat, end.lat, progress)
    val lon = lerp(start.lon, end.lon, progress)
    // ...
}
```

---

## 12. 测试验证

### 12.1 测试方法

1. **户外直道测试**
   - 匀速直线行驶
   - 对比GPS轨迹和实际路线

2. **定点往返测试**
   - 起点到终点往返
   - 检查轨迹闭合性

3. **急转弯测试**
   - 标记急转弯位置
   - 检查轨迹是否记录

4. **长距离测试**
   - 行驶1公里以上
   - 检查轨迹累积误差

### 12.2 预期结果

| 测试 | 预期 | 误差容忍 |
|------|------|----------|
| 直道 | 直线轨迹 | < 2米 |
| 往返 | 起点重合 | < 1米 |
| 急转弯 | 平滑曲线 | 无明显折角 |
| 长距离 | 轨迹连续 | 无跳跃 |

---

## 13. 参考资料

1. Madgwick, S. (2010). An efficient orientation filter for inertial and inertial/magnetic sensor arrays. Report x-io Technologies.

2. GPS Positioning Fundamentals - Navipedia

3. Android Sensor API Documentation

---

## 14. 版本历史

| 版本 | 日期 | 修改内容 |
|------|------|----------|
| 1.0 | 2026-05-21 | 初始版本 |

---

## 15. 联系方式

如有问题或建议，请联系开发团队。

---

**文档结束**
