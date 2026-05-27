import xml.etree.ElementTree as ET
import numpy as np
from scipy import signal
import matplotlib.pyplot as plt
import os
import requests
from PIL import Image
from io import BytesIO

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei']  # 使用黑体
plt.rcParams['axes.unicode_minus'] = False  # 解决负号显示问题

from datetime import datetime
import math
from tqdm import tqdm

# 星图地球数据云的token
token = "c02365916bc5b20fdfd7a97f9a373bf0b8ecfb3baf3887c574aed6e818de6302"

# 瓦片URL模板
tile_url = "https://tiles1.geovisearth.com/base/v1/img/{z}/{x}/{y}?format=webp&tmsIds=w&token=" + token

def haversine_distance(lat1, lon1, lat2, lon2):
    """使用Haversine公式计算两个经纬度点之间的距离（单位：米）"""
    R = 6371000  # 地球半径（米）
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
    # 计算边界的瓦片坐标
    min_x, min_y = latlon_to_tile(max_lat, min_lon, zoom)
    max_x, max_y = latlon_to_tile(min_lat, max_lon, zoom)
    
    # 计算总瓦片数
    total_tiles = (max_x - min_x + 1) * (max_y - min_y + 1)
    print(f"需要绘制 {total_tiles} 个瓦片...")
    
    # 绘制每个瓦片，添加进度条
    tile_count = 0
    with tqdm(total=total_tiles, desc="绘制瓦片") as pbar:
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                # 获取瓦片图像
                img = get_tile_image(x, y, zoom)
                if img is not None:
                    # 计算瓦片的经纬度范围
                    n = 2.0 ** zoom
                    tile_min_lon = x * 360.0 / n - 180.0
                    tile_max_lon = (x + 1) * 360.0 / n - 180.0
                    
                    # 计算瓦片的纬度范围（更准确的计算）
                    tile_max_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
                    tile_min_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + 1) / n))))
                    
                    # 绘制瓦片，设置zorder为0，确保在底层
                    ax.imshow(img, extent=[tile_min_lon, tile_max_lon, tile_min_lat, tile_max_lat], alpha=0.8, zorder=0)
                # 更新进度条
                pbar.update(1)

def read_gpx(file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    # 处理命名空间
    namespace = '{http://www.topografix.com/GPX/1/1}'
    
    points = []
    times = []
    for trk in root.findall(namespace + 'trk'):
        for trkseg in trk.findall(namespace + 'trkseg'):
            for trkpt in trkseg.findall(namespace + 'trkpt'):
                lat = float(trkpt.get('lat'))
                lon = float(trkpt.get('lon'))
                points.append((lat, lon))
                
                # 读取时间数据
                time_elem = trkpt.find(namespace + 'time')
                if time_elem is not None:
                    time_str = time_elem.text
                    # 解析时间字符串
                    time = datetime.strptime(time_str, '%Y-%m-%dT%H:%M:%S.%fZ')
                    times.append(time)
    
    return np.array(points), times

def smooth_track(points, window_size=11, polyorder=3):
    if len(points) < window_size:
        print(f"警告：轨迹点数量({len(points)})小于窗口大小({window_size})，使用默认窗口大小")
        window_size = min(len(points), 5)
    
    latitudes = points[:, 0]
    longitudes = points[:, 1]
    
    # 使用Savitzky-Golay滤波器进行平滑
    smoothed_lat = signal.savgol_filter(latitudes, window_size, polyorder)
    smoothed_lon = signal.savgol_filter(longitudes, window_size, polyorder)
    
    return np.column_stack((smoothed_lat, smoothed_lon))

def calculate_speeds(points, times):
    """计算每个点的速度"""
    if len(points) < 2 or len(times) < 2:
        return []
    
    speeds = []
    
    for i in range(1, len(points)):
        # 计算距离
        lat1, lon1 = points[i-1]
        lat2, lon2 = points[i]
        distance = haversine_distance(lat1, lon1, lat2, lon2)
        
        # 计算时间差（秒）
        time_diff = (times[i] - times[i-1]).total_seconds()
        
        # 计算速度（米/秒）
        if time_diff > 0:
            speed = distance / time_diff
            speeds.append(speed)
        else:
            speeds.append(0)
    
    # 为了与轨迹点数量匹配，在开头添加一个速度值
    speeds.insert(0, speeds[0] if speeds else 0)
    
    return speeds

def get_color_from_speed(speed, max_speed):
    """根据速度获取颜色（慢=绿，快=红）"""
    # 速度阈值
    min_speed = 0  # 最小速度（米/秒）
    
    # 限制速度范围
    speed = max(min_speed, min(speed, max_speed))
    
    # 计算颜色比例
    if max_speed > min_speed:
        ratio = (speed - min_speed) / (max_speed - min_speed)
    else:
        ratio = 0
    
    # 从绿色(0,1,0)过渡到红色(1,0,0)
    r = ratio
    g = 1 - ratio
    b = 0
    
    return (r, g, b)

def calculate_zoom_level(min_lat, max_lat, min_lon, max_lon):
    """根据面积大小选择合适的瓦片缩放级别（15~18）"""
    # 计算边界框的宽度和高度（经纬度差）
    lat_diff = max_lat - min_lat
    lon_diff = max_lon - min_lon
    
    # 计算对角线长度（近似面积）
    diagonal = math.sqrt(lat_diff**2 + lon_diff**2)
    
    # 根据对角线长度选择缩放级别
    if diagonal > 0.01:
        return 15  # 面积较大，使用较低的缩放级别
    elif diagonal > 0.005:
        return 16
    elif diagonal > 0.002:
        return 17
    else:
        return 18  # 面积较小，使用最高的缩放级别

def plot_track_with_speed_color(original_points, smoothed_points, speeds, file_name):
    # 计算经纬度范围，不添加边距以避免留白
    min_lat = min(min(original_points[:, 0]), min(smoothed_points[:, 0]))
    max_lat = max(max(original_points[:, 0]), max(smoothed_points[:, 0]))
    min_lon = min(min(original_points[:, 1]), min(smoothed_points[:, 1]))
    max_lon = max(max(original_points[:, 1]), max(smoothed_points[:, 1]))
    
    # 计算宽高比
    lat_range = max_lat - min_lat
    lon_range = max_lon - min_lon
    aspect_ratio = lon_range / lat_range
    
    # 确保最短边的长度不少于1200
    if aspect_ratio > 1:
        # 宽屏，以高度为基准
        height = 12
        width = height * aspect_ratio
    else:
        # 竖屏，以宽度为基准
        width = 12
        height = width / aspect_ratio
    
    # 提高分辨率，dpi设置为100，确保最短边不少于1200
    plt.figure(figsize=(width, height), dpi=100)
    ax = plt.gca()
    
    # 根据面积大小选择合适的瓦片缩放级别
    zoom_level = calculate_zoom_level(min_lat, max_lat, min_lon, max_lon)
    print(f"根据面积大小选择的瓦片缩放级别: {zoom_level}")
    
    # 计算最大速度
    max_speed = max(speeds) if speeds else 1
    
    # 绘制地图背景
    plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom=zoom_level)
    
    # 绘制轨迹，根据速度改变颜色，添加进度条
    print("正在绘制轨迹...")
    for i in tqdm(range(len(smoothed_points) - 1), desc="绘制进度"):
        # 获取当前段的平均速度
        avg_speed = (speeds[i] + speeds[i+1]) / 2
        # 获取对应颜色
        color = get_color_from_speed(avg_speed, max_speed)
        # 绘制线段
        ax.plot([smoothed_points[i, 1], smoothed_points[i+1, 1]], 
                [smoothed_points[i, 0], smoothed_points[i+1, 0]], 
                color=color, linewidth=2, zorder=1)
    
    # 标记起点和终点
    ax.plot(smoothed_points[0, 1], smoothed_points[0, 0], 'go', markersize=8, label='起点', zorder=2)
    ax.plot(smoothed_points[-1, 1], smoothed_points[-1, 0], 'ro', markersize=8, label='终点', zorder=2)
    
    # 设置标题和标签
    plt.title(f'{file_name} 速度轨迹')
    plt.xlabel('经度')
    plt.ylabel('纬度')
    plt.legend()
    
    # 隐藏网格
    plt.grid(False)
    
    # 设置经纬度比例一致
    ax.set_aspect('equal', adjustable='box')
    
    # 设置坐标轴范围，不添加边距以避免留白
    ax.set_xlim(min_lon, max_lon)
    ax.set_ylim(min_lat, max_lat)
    
    # 移除留白
    plt.subplots_adjust(left=0, right=1, top=0.95, bottom=0)
    
    # 保存图表
    output_file = os.path.splitext(file_name)[0] + '_speed_color.png'
    plt.savefig(output_file, dpi=100, bbox_inches='tight', pad_inches=0)
    plt.close()
    print(f"图表已保存为: {output_file}")

def write_gpx(output_file, smoothed_points, times=None):
    # 创建GPX文件结构
    root = ET.Element('gpx', version='1.1', creator='GPX Speed Color', xmlns='http://www.topografix.com/GPX/1/1')
    trk = ET.SubElement(root, 'trk')
    trkseg = ET.SubElement(trk, 'trkseg')
    
    for i, (lat, lon) in enumerate(smoothed_points):
        trkpt = ET.SubElement(trkseg, 'trkpt', lat=str(lat), lon=str(lon))
        # 添加时间数据（如果有）
        if times and i < len(times):
            time_elem = ET.SubElement(trkpt, 'time')
            time_elem.text = times[i].strftime('%Y-%m-%dT%H:%M:%S.%fZ')
    
    # 写入文件
    tree = ET.ElementTree(root)
    tree.write(output_file, encoding='utf-8', xml_declaration=True)
    print(f"平滑后的轨迹已保存为: {output_file}")

def main():
    # 获取当前目录下的GPX文件，排除已经处理过的文件
    print("正在搜索GPX文件...")
    gpx_files = [f for f in os.listdir('.') if f.lower().endswith('.gpx') and '_speed_color' not in f and '_smoothed' not in f]
    
    if not gpx_files:
        print("未找到GPX文件")
        return
    
    print(f"找到 {len(gpx_files)} 个GPX文件")
    
    for gpx_file in gpx_files:
        print(f"\n处理文件: {gpx_file}")
        
        # 读取GPX文件
        print("正在读取GPX文件...")
        points, times = read_gpx(gpx_file)
        print(f"原始轨迹点数量: {len(points)}")
        print(f"时间数据点数量: {len(times)}")
        
        if len(points) < 3:
            print("轨迹点数量不足，无法进行平滑处理")
            continue
        
        # 平滑轨迹
        print("正在平滑轨迹...")
        smoothed_points = smooth_track(points)
        print(f"平滑后轨迹点数量: {len(smoothed_points)}")
        
        # 计算速度
        print("正在计算速度...")
        speeds = calculate_speeds(smoothed_points, times)
        print(f"速度计算完成，共 {len(speeds)} 个速度数据点")
        
        # 绘制带速度颜色的轨迹
        print("正在绘制带速度颜色的轨迹...")
        plot_track_with_speed_color(points, smoothed_points, speeds, gpx_file)
        
        # 保存平滑后的GPX文件
        print("正在保存平滑后的GPX文件...")
        output_file = os.path.splitext(gpx_file)[0] + '_speed_color.gpx'
        write_gpx(output_file, smoothed_points, times)
        
        print("处理完成！")
        print("-" * 50)

if __name__ == "__main__":
    # 安装必要的依赖
    try:
        import numpy
        import scipy
        import matplotlib
        import requests
        from PIL import Image
        from tqdm import tqdm
    except ImportError:
        print("正在安装必要的依赖...")
        import subprocess
        subprocess.check_call(['pip', 'install', 'numpy', 'scipy', 'matplotlib', 'requests', 'Pillow', 'tqdm'])
        print("依赖安装完成")
    
    main()
