import json
import numpy as np
import matplotlib.pyplot as plt
import os
import requests
from PIL import Image
from io import BytesIO
from datetime import datetime
import math
from tqdm import tqdm
from scipy import signal

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False

# 星图地球数据云的token
token = "c02365916bc5b20fdfd7a97f9a373bf0b8ecfb3baf3887c574aed6e818de6302"

# 瓦片URL模板
tile_url = "https://tiles1.geovisearth.com/base/v1/img/{z}/{x}/{y}?format=webp&tmsIds=w&token=" + token

def haversine_distance(lat1, lon1, lat2, lon2):
    """使用Haversine公式计算两个经纬度点之间的距离（单位：米）"""
    R = 6371000
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    
    a = math.sin(delta_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    
    return R * c

def latlon_to_tile(lat, lon, zoom):
    """将经纬度转换为瓦片坐标"""
    n = 2.0 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n)
    return x, y

def get_tile_image(x, y, z):
    """获取指定瓦片的图像"""
    url = tile_url.format(z=z, x=x, y=y)
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        img = Image.open(BytesIO(response.content))
        return img
    except Exception as e:
        print(f"获取瓦片失败: {e} - URL: {url}")
        return None

def plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom=18):
    """在指定的坐标轴上绘制地图背景"""
    min_x, min_y = latlon_to_tile(max_lat, min_lon, zoom)
    max_x, max_y = latlon_to_tile(min_lat, max_lon, zoom)
    
    total_tiles = (max_x - min_x + 1) * (max_y - min_y + 1)
    print(f"需要绘制 {total_tiles} 个瓦片...")
    
    with tqdm(total=total_tiles, desc="绘制瓦片") as pbar:
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                img = get_tile_image(x, y, zoom)
                if img is not None:
                    n = 2.0 ** zoom
                    tile_min_lon = x * 360.0 / n - 180.0
                    tile_max_lon = (x + 1) * 360.0 / n - 180.0
                    tile_max_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
                    tile_min_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + 1) / n))))
                    ax.imshow(img, extent=[tile_min_lon, tile_max_lon, tile_min_lat, tile_max_lat], alpha=0.8, zorder=0)
                pbar.update(1)

def read_raw_track(file_path):
    """读取原始轨迹JSON数据"""
    print("正在读取原始轨迹数据...")
    raw_data = []
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    raw_data.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    
    # 找到第一个GPS点的位置
    first_gps_idx = -1
    for i, point in enumerate(raw_data):
        if point.get('type') == 'GPS':
            first_gps_idx = i
            break
    
    if first_gps_idx == -1:
        print("错误：未找到GPS数据")
        return None, None
    
    print(f"第一个GPS点位置: {first_gps_idx}")
    filtered_data = raw_data[first_gps_idx:]
    
    # 提取GPS点
    points = []
    timestamps = []
    
    for point in filtered_data:
        if point.get('type') == 'GPS':
            lat = point.get('gpsLat', 0)
            lon = point.get('gpsLon', 0)
            timestamp = point.get('timestamp', 0)
            speed = point.get('gpsSpeed', 0)
            
            # 创建伪时间对象用于后续计算
            # 这里我们用相对时间
            points.append((lat, lon))
            timestamps.append(timestamp)
    
    print(f"GPS点数量: {len(points)}")
    return np.array(points), timestamps

def smooth_track(points, window_size=11, polyorder=3):
    """平滑轨迹"""
    if len(points) < window_size:
        window_size = min(len(points), 5)
    
    latitudes = points[:, 0]
    longitudes = points[:, 1]
    
    smoothed_lat = signal.savgol_filter(latitudes, window_size, polyorder)
    smoothed_lon = signal.savgol_filter(longitudes, window_size, polyorder)
    
    return np.column_stack((smoothed_lat, smoothed_lon))

def calculate_speeds(points, timestamps):
    """计算每个点的速度"""
    if len(points) < 2 or len(timestamps) < 2:
        return []
    
    speeds = []
    
    for i in range(1, len(points)):
        lat1, lon1 = points[i-1]
        lat2, lon2 = points[i]
        distance = haversine_distance(lat1, lon1, lat2, lon2)
        
        time_diff = timestamps[i] - timestamps[i-1]
        
        if time_diff > 0:
            speed = distance / time_diff
            speeds.append(speed)
        else:
            speeds.append(0)
    
    speeds.insert(0, speeds[0] if speeds else 0)
    return speeds

def get_color_from_speed(speed, max_speed):
    """根据速度获取颜色（慢=绿，快=红）"""
    min_speed = 0
    speed = max(min_speed, min(speed, max_speed))
    
    if max_speed > min_speed:
        ratio = (speed - min_speed) / (max_speed - min_speed)
    else:
        ratio = 0
    
    r = ratio
    g = 1 - ratio
    b = 0
    return (r, g, b)

def calculate_zoom_level(min_lat, max_lat, min_lon, max_lon):
    """根据面积大小选择合适的瓦片缩放级别（17~18，最高18）"""
    lat_diff = max_lat - min_lat
    lon_diff = max_lon - min_lon
    diagonal = math.sqrt(lat_diff**2 + lon_diff**2)
    
    if diagonal > 0.01:
        return 17
    elif diagonal > 0.005:
        return 18
    else:
        return 18

def plot_track_with_speed_color(original_points, smoothed_points, speeds, file_name):
    """绘制带速度颜色和卫星图背景的轨迹"""
    min_lat = min(min(original_points[:, 0]), min(smoothed_points[:, 0]))
    max_lat = max(max(original_points[:, 0]), max(smoothed_points[:, 0]))
    min_lon = min(min(original_points[:, 1]), min(smoothed_points[:, 1]))
    max_lon = max(max(original_points[:, 1]), max(smoothed_points[:, 1]))
    
    lat_range = max_lat - min_lat
    lon_range = max_lon - min_lon
    aspect_ratio = lon_range / lat_range
    
    if aspect_ratio > 1:
        height = 12
        width = height * aspect_ratio
    else:
        width = 12
        height = width / aspect_ratio
    
    plt.figure(figsize=(width, height), dpi=100)
    ax = plt.gca()
    
    zoom_level = calculate_zoom_level(min_lat, max_lat, min_lon, max_lon)
    print(f"瓦片缩放级别: {zoom_level}")
    
    max_speed = max(speeds) if speeds else 1
    
    plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom=zoom_level)
    
    print("正在绘制轨迹...")
    for i in tqdm(range(len(smoothed_points) - 1), desc="绘制进度"):
        avg_speed = (speeds[i] + speeds[i+1]) / 2
        color = get_color_from_speed(avg_speed, max_speed)
        ax.plot([smoothed_points[i, 1], smoothed_points[i+1, 1]], 
                [smoothed_points[i, 0], smoothed_points[i+1, 0]], 
                color=color, linewidth=2, zorder=1)
    
    ax.plot(smoothed_points[0, 1], smoothed_points[0, 0], 'go', markersize=8, label='起点', zorder=2)
    ax.plot(smoothed_points[-1, 1], smoothed_points[-1, 0], 'ro', markersize=8, label='终点', zorder=2)
    
    plt.title(f'{file_name} 卡丁车轨迹')
    plt.xlabel('经度')
    plt.ylabel('纬度')
    plt.legend()
    plt.grid(False)
    ax.set_aspect('equal', adjustable='box')
    ax.set_xlim(min_lon, max_lon)
    ax.set_ylim(min_lat, max_lat)
    plt.subplots_adjust(left=0, right=1, top=0.95, bottom=0)
    
    output_file = os.path.splitext(file_name)[0] + '_track.png'
    plt.savefig(output_file, dpi=100, bbox_inches='tight', pad_inches=0)
    plt.close()
    print(f"轨迹图已保存为: {output_file}")

def main():
    raw_files = [f for f in os.listdir('.') if f.lower().endswith('.json') and 'raw' in f.lower()]
    
    if not raw_files:
        print("未找到原始轨迹JSON文件")
        return
    
    for raw_file in raw_files:
        print(f"\n处理文件: {raw_file}")
        
        points, timestamps = read_raw_track(raw_file)
        if points is None or len(points) < 3:
            continue
        
        print("正在平滑轨迹...")
        smoothed_points = smooth_track(points)
        
        print("正在计算速度...")
        speeds = calculate_speeds(smoothed_points, timestamps)
        
        print("正在绘制轨迹...")
        plot_track_with_speed_color(points, smoothed_points, speeds, raw_file)
        
        print("处理完成！")
        print("-" * 50)

if __name__ == "__main__":
    main()
