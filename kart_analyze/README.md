# Kart 轨迹可视化工具

## 文件说明

- `raw_track_visualizer.py` - 新创建的原始轨迹可视化脚本，用于处理JSON格式的原始轨迹数据
- `gpx_speed_color.py` - GPX文件速度颜色可视化脚本
- `gpx_smoother.py` - GPX文件平滑脚本

## 使用方法

### 处理原始JSON轨迹数据

1. 确保 `raw_track_*.json` 文件在当前目录
2. 运行 `python raw_track_visualizer.py`
3. 脚本会自动：
   - 读取原始JSON轨迹数据
   - 过滤掉第一个GPS点之前的数据
   - 平滑轨迹
   - 计算速度
   - 下载卫星图瓦片作为背景
   - 生成带速度颜色的轨迹图

### 功能特性

- 卫星图背景（使用星图地球数据云）
- 速度颜色映射（绿色=慢，红色=快）
- 正确的经纬度比例（不会扁）
- 轨迹平滑
- 起点/终点标记
