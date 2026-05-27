import xml.etree.ElementTree as ET
import numpy as np
from scipy.signal import savgol_filter
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
        print(f"成功获取瓦片: {url}")
        return img
    except Exception as e:
        print(f"获取瓦片失败: {e} - URL: {url}")
        return None

def plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom=18):
    """在指定的坐标轴上绘制地图背景"""
    # 计算边界的瓦片坐标
    min_x, min_y = latlon_to_tile(max_lat, min_lon, zoom)
    max_x, max_y = latlon_to_tile(min_lat, max_lon, zoom)
    
    # 绘制每个瓦片
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
    smoothed_lat = savgol_filter(latitudes, window_size, polyorder)
    smoothed_lon = savgol_filter(longitudes, window_size, polyorder)
    
    return np.column_stack((smoothed_lat, smoothed_lon))

def plot_track(original_points, smoothed_points, file_name):
    plt.figure(figsize=(10, 6))
    ax = plt.gca()
    
    # 计算经纬度范围，添加一些边距
    min_lat = min(min(original_points[:, 0]), min(smoothed_points[:, 0])) - 0.001
    max_lat = max(max(original_points[:, 0]), max(smoothed_points[:, 0])) + 0.001
    min_lon = min(min(original_points[:, 1]), min(smoothed_points[:, 1])) - 0.001
    max_lon = max(max(original_points[:, 1]), max(smoothed_points[:, 1])) + 0.001
    
    # 绘制地图背景
    plot_map_background(ax, min_lat, max_lat, min_lon, max_lon)
    
    # 绘制轨迹，设置zorder为1，确保在地图之上
    ax.plot(original_points[:, 1], original_points[:, 0], 'b-', alpha=0.5, label='原始轨迹', zorder=1)
    ax.plot(smoothed_points[:, 1], smoothed_points[:, 0], 'r-', label='平滑轨迹', zorder=1)
    
    # 设置标题和标签
    plt.title(f'{file_name} 轨迹平滑效果')
    plt.xlabel('经度')
    plt.ylabel('纬度')
    plt.legend()
    
    # 隐藏网格
    plt.grid(False)
    
    # 设置经纬度比例一致，使用set_aspect替代axis('equal')
    ax.set_aspect('equal', adjustable='box')
    
    # 设置坐标轴范围
    ax.set_xlim(min_lon, max_lon)
    ax.set_ylim(min_lat, max_lat)
    
    plt.tight_layout()
    
    # 保存图表
    output_file = os.path.splitext(file_name)[0] + '_smoothed.png'
    plt.savefig(output_file)
    plt.close()
    print(f"图表已保存为: {output_file}")

def write_gpx(output_file, smoothed_points, times=None):
    # 创建GPX文件结构
    root = ET.Element('gpx', version='1.1', creator='GPX Smoother', xmlns='http://www.topografix.com/GPX/1/1')
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

def analyze_laps(points, times):
    """分析轨迹数据，计算速度和圈数"""
    if len(points) < 2 or len(times) < 2:
        print("数据点不足，无法进行分析")
        return [], []
    
    # 计算每段的距离和时间差
    distances = []
    time_diffs = []
    speeds = []
    
    for i in range(1, len(points)):
        # 计算距离
        lat1, lon1 = points[i-1]
        lat2, lon2 = points[i]
        distance = haversine_distance(lat1, lon1, lat2, lon2)
        distances.append(distance)
        
        # 计算时间差（秒）
        time_diff = (times[i] - times[i-1]).total_seconds()
        time_diffs.append(time_diff)
        
        # 计算速度（米/秒）
        if time_diff > 0:
            speed = distance / time_diff
            speeds.append(speed)
        else:
            speeds.append(0)
    
    # 检测圈数（通过检测是否回到起点附近）
    start_lat, start_lon = points[0]
    lap_threshold = 50  # 50米阈值，判断是否回到起点
    lat_lon_threshold = 0.00025  # 经度和纬度的阈值
    laps = []
    current_lap = [0]  # 记录每圈的起点索引
    
    for i in range(1, len(points)):
        lat, lon = points[i]
        distance_to_start = haversine_distance(lat, lon, start_lat, start_lon)
        lat_diff = abs(lat - start_lat)
        lon_diff = abs(lon - start_lon)
        
        # 确保回到起点附近，并且经度和纬度的差距都在阈值范围内
        if (distance_to_start < lap_threshold and 
            lat_diff < lat_lon_threshold and 
            lon_diff < lat_lon_threshold and 
            i - current_lap[-1] > 10):  # 确保不是同一位置
            current_lap.append(i)
            laps.append(current_lap)
            current_lap = [i]
    
    # 处理最后一圈
    if current_lap and len(current_lap) > 1:
        laps.append(current_lap)
    
    # 分析每圈的速度
    print(f"\n检测到 {len(laps)} 圈")
    lap_data = []
    for i, lap in enumerate(laps):
        if len(lap) < 2:
            continue
        
        lap_start = lap[0]
        lap_end = lap[1]
        lap_distance = sum(distances[lap_start:lap_end])
        lap_time = sum(time_diffs[lap_start:lap_end])
        lap_speeds = speeds[lap_start:lap_end]
        
        if lap_time > 0:
            avg_speed = lap_distance / lap_time
            max_speed = max(lap_speeds) if lap_speeds else 0
            min_speed = min(lap_speeds) if lap_speeds else 0
            
            print(f"第 {i+1} 圈:")
            print(f"  距离: {lap_distance:.2f} 米")
            print(f"  时间: {lap_time:.2f} 秒")
            print(f"  平均速度: {avg_speed:.2f} 米/秒 ({avg_speed*3.6:.2f} 公里/小时)")
            print(f"  最大速度: {max_speed:.2f} 米/秒 ({max_speed*3.6:.2f} 公里/小时)")
            print(f"  最小速度: {min_speed:.2f} 米/秒 ({min_speed*3.6:.2f} 公里/小时)")
            
            # 保存每圈的数据
            lap_data.append({
                'lap_number': i+1,
                'start_index': lap_start,
                'end_index': lap_end,
                'distance': lap_distance,
                'time': lap_time,
                'avg_speed': avg_speed,
                'max_speed': max_speed,
                'min_speed': min_speed
            })
    
    # 计算整体统计数据
    total_distance = sum(distances)
    total_time = sum(time_diffs)
    avg_speed_overall = total_distance / total_time if total_time > 0 else 0
    max_speed_overall = max(speeds) if speeds else 0
    
    print(f"\n整体统计:")
    print(f"总距离: {total_distance:.2f} 米")
    print(f"总时间: {total_time:.2f} 秒")
    print(f"平均速度: {avg_speed_overall:.2f} 米/秒 ({avg_speed_overall*3.6:.2f} 公里/小时)")
    print(f"最大速度: {max_speed_overall:.2f} 米/秒 ({max_speed_overall*3.6:.2f} 公里/小时)")
    
    return speeds, laps, lap_data

def plot_speed_analysis(speeds, laps, file_name):
    """绘制速度分析图表"""
    plt.figure(figsize=(12, 6))
    
    # 绘制速度曲线
    plt.plot(speeds, 'b-', alpha=0.7, label='速度')
    
    # 标记圈数
    for i, lap in enumerate(laps):
        if len(lap) >= 2:
            plt.axvline(x=lap[0], color='r', linestyle='--', alpha=0.5)
            plt.text(lap[0], max(speeds)*0.9, f'第{i+1}圈', rotation=90, verticalalignment='top')
    
    plt.title(f'{file_name} 速度分析')
    plt.xlabel('数据点')
    plt.ylabel('速度 (米/秒)')
    plt.legend()
    plt.grid(True)
    plt.tight_layout()
    
    # 保存图表
    output_file = os.path.splitext(file_name)[0] + '_speed_analysis.png'
    plt.savefig(output_file)
    plt.close()
    print(f"速度分析图表已保存为: {output_file}")

def plot_lap_trajectory(points, lap_data, speeds, file_name):
    """为每圈生成单独的轨迹图"""
    for lap_info in lap_data:
        lap_number = lap_info['lap_number']
        start_index = lap_info['start_index']
        end_index = lap_info['end_index']
        distance = lap_info['distance']
        time = lap_info['time']
        avg_speed = lap_info['avg_speed']
        max_speed = lap_info['max_speed']
        min_speed = lap_info['min_speed']
        
        # 提取该圈的轨迹点和速度数据
        lap_points = points[start_index:end_index+1]
        lap_speeds = speeds[start_index:end_index]  # 速度数据比轨迹点少一个
        
        plt.figure(figsize=(12, 7))
        ax = plt.gca()
        
        # 计算经纬度范围，添加一些边距
        min_lat = min(lap_points[:, 0]) - 0.001
        max_lat = max(lap_points[:, 0]) + 0.001
        min_lon = min(lap_points[:, 1]) - 0.001
        max_lon = max(lap_points[:, 1]) + 0.001
        
        # 绘制地图背景
        plot_map_background(ax, min_lat, max_lat, min_lon, max_lon)
        
        # 绘制轨迹，标记速度>60km/h的部分，设置zorder为1，确保在地图之上
        for i in range(len(lap_points) - 1):
            speed_kmh = lap_speeds[i] * 3.6
            if speed_kmh > 60:
                # 速度>60km/h的部分用红色线段标记
                ax.plot([lap_points[i, 1], lap_points[i+1, 1]], 
                         [lap_points[i, 0], lap_points[i+1, 0]], 
                         'r-', linewidth=2, zorder=1)
            else:
                # 正常速度用蓝色线段标记
                ax.plot([lap_points[i, 1], lap_points[i+1, 1]], 
                         [lap_points[i, 0], lap_points[i+1, 0]], 
                         'b-', zorder=1)
        
        # 标记起点和终点，设置zorder为2，确保在轨迹之上
        ax.plot(lap_points[0, 1], lap_points[0, 0], 'go', markersize=8, label='起点', zorder=2)
        ax.plot(lap_points[-1, 1], lap_points[-1, 0], 'ro', markersize=8, label='终点', zorder=2)
        
        # 标记最快速度点
        if lap_speeds:
            max_speed_index = lap_speeds.index(max(lap_speeds))
            ax.plot(lap_points[max_speed_index, 1], lap_points[max_speed_index, 0], 
                     'y*', markersize=10, label='最快速度点', zorder=2)
            # 在旁边标注速度
            ax.text(lap_points[max_speed_index, 1] + 0.00005, 
                     lap_points[max_speed_index, 0] + 0.00005, 
                     f'最快: {max(lap_speeds)*3.6:.1f} km/h', 
                     fontsize=8, bbox=dict(facecolor='yellow', alpha=0.5), zorder=2)
            
            # 标记最小速度点
            min_speed_index = lap_speeds.index(min(lap_speeds))
            ax.plot(lap_points[min_speed_index, 1], lap_points[min_speed_index, 0], 
                     'c*', markersize=10, label='最小速度点', zorder=2)
            # 在旁边标注速度
            ax.text(lap_points[min_speed_index, 1] + 0.00005, 
                     lap_points[min_speed_index, 0] + 0.00005, 
                     f'最慢: {min(lap_speeds)*3.6:.1f} km/h', 
                     fontsize=8, bbox=dict(facecolor='cyan', alpha=0.5), zorder=2)
        
        plt.title(f'{file_name} 第{lap_number}圈轨迹')
        plt.xlabel('经度')
        plt.ylabel('纬度')
        plt.legend()
        
        # 隐藏网格
        plt.grid(False)
        
        # 设置经纬度比例一致，使用set_aspect替代axis('equal')
        ax.set_aspect('equal', adjustable='box')
        
        # 设置坐标轴范围
        ax.set_xlim(min_lon, max_lon)
        ax.set_ylim(min_lat, max_lat)
        
        # 在图片底部添加每圈的数据
        plt.figtext(0.5, 0.01, 
                   f'距离: {distance:.2f} 米 | 时间: {time:.2f} 秒 | 平均速度: {avg_speed*3.6:.2f} km/h | 最大速度: {max_speed*3.6:.2f} km/h | 最小速度: {min_speed*3.6:.2f} km/h',
                   ha="center", fontsize=9)
        
        plt.tight_layout(rect=[0, 0.05, 1, 0.95])  # 调整布局，为底部文本留出空间
        
        # 保存图表
        output_file = os.path.splitext(file_name)[0] + f'_lap_{lap_number}.png'
        plt.savefig(output_file)
        plt.close()
        print(f"第{lap_number}圈轨迹图已保存为: {output_file}")

def main():
    # 获取当前目录下的GPX文件，排除已经平滑过的文件
    gpx_files = [f for f in os.listdir('.') if f.lower().endswith('.gpx') and '_smoothed' not in f]
    
    if not gpx_files:
        print("未找到GPX文件")
        return
    
    for gpx_file in gpx_files:
        print(f"处理文件: {gpx_file}")
        
        # 读取GPX文件
        points, times = read_gpx(gpx_file)
        print(f"原始轨迹点数量: {len(points)}")
        print(f"时间数据点数量: {len(times)}")
        
        if len(points) < 3:
            print("轨迹点数量不足，无法进行平滑处理")
            continue
        
        # 平滑轨迹
        smoothed_points = smooth_track(points)
        print(f"平滑后轨迹点数量: {len(smoothed_points)}")
        
        # 绘制轨迹对比图
        plot_track(points, smoothed_points, gpx_file)
        
        # 保存平滑后的GPX文件
        output_file = os.path.splitext(gpx_file)[0] + '_smoothed.gpx'
        write_gpx(output_file, smoothed_points, times)
        
        # 分析速度和圈数
        if len(times) == len(points):
            speeds, laps, lap_data = analyze_laps(smoothed_points, times)
            # 绘制速度分析图表
            plot_speed_analysis(speeds, laps, gpx_file)
            # 绘制每圈轨迹图
            plot_lap_trajectory(smoothed_points, lap_data, speeds, gpx_file)
        else:
            print("时间数据不完整，无法进行速度分析")
        
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
    except ImportError:
        print("正在安装必要的依赖...")
        import subprocess
        subprocess.check_call(['pip', 'install', 'numpy', 'scipy', 'matplotlib', 'requests', 'Pillow'])
        print("依赖安装完成")
    
    main()