import json
import numpy as np
import matplotlib.pyplot as plt
import os
import requests
from PIL import Image
from io import BytesIO
import math
from tqdm import tqdm
from scipy.interpolate import interp1d
from datetime import datetime, timedelta

plt.rcParams['font.sans-serif'] = ['SimHei']
plt.rcParams['axes.unicode_minus'] = False

token = "c02365916bc5b20fdfd7a97f9a373bf0b8ecfb3baf3887c574aed6e818de6302"
tile_url = "https://tiles1.geovisearth.com/base/v1/img/{z}/{x}/{y}?format=webp&tmsIds=w&token=" + token

METERS_PER_DEGREE_LAT = 111000.0

def latlon_to_tile(lat, lon, zoom):
    """将经纬度转换为瓦片坐标（Web Mercator投影）"""
    n = 2.0 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int(1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi / 2.0 * n)
    return x, y

def get_tile_image(x, y, z):
    """下载指定瓦片图片"""
    url = tile_url.format(z=z, x=x, y=y)
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        return Image.open(BytesIO(response.content))
    except Exception as e:
        print(f"  瓦片下载失败 ({x},{y},{z}): {str(e)}")
        return None

def plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom=18):
    """绘制卫星地图背景"""
    # 计算瓦片范围
    min_x, min_y = latlon_to_tile(max_lat, min_lon, zoom)
    max_x, max_y = latlon_to_tile(min_lat, max_lon, zoom)
    
    total_tiles = (max_x - min_x + 1) * (max_y - min_y + 1)
    print(f"下载 {total_tiles} 个地图瓦片...")
    
    downloaded_count = 0
    failed_count = 0
    
    # 生成所有瓦片坐标对
    tile_coords = [(x, y) for x in range(min_x, max_x + 1) for y in range(min_y, max_y + 1)]
    
    for x, y in tqdm(tile_coords, desc="下载瓦片", total=total_tiles):
        img = get_tile_image(x, y, zoom)
        if img:
            downloaded_count += 1
            n = 2.0 ** zoom
            tile_min_lon = x * 360.0 / n - 180.0
            tile_max_lon = (x + 1) * 360.0 / n - 180.0
            tile_max_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
            tile_min_lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + 1) / n))))
            # zorder=0 确保地图在最底层
            ax.imshow(img, extent=[tile_min_lon, tile_max_lon, tile_min_lat, tile_max_lat], 
                      alpha=1.0, zorder=0)
        else:
            failed_count += 1
    
    print(f"瓦片下载完成: 成功 {downloaded_count}, 失败 {failed_count}")
    
    if downloaded_count == 0:
        print("警告：所有瓦片下载失败，将使用空白背景")
        ax.set_facecolor('#e0e0e0')

def read_raw_track(file_path):
    """读取原始轨迹数据"""
    print(f"读取: {file_path}")
    gps_points = []
    imu_data = []
    
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    for line in tqdm(lines, desc="解析数据"):
        line = line.strip()
        if not line:
            continue
        try:
            data = json.loads(line)
            if data.get('type') == 'GPS':
                gps_points.append({
                    'timestamp': data['timestamp'],
                    'lat': data['gpsLat'],
                    'lon': data['gpsLon'],
                    'speed': data.get('gpsSpeed', 0),
                    'bearing': data.get('gpsBearing', 0),
                    'alt': data.get('gpsAlt', 0)
                })
            elif data.get('type') == 'IMU':
                imu_data.append({
                    'timestamp': data['timestamp'],
                    'accelX': data['accelX'],
                    'accelY': data['accelY'],
                    'accelZ': data['accelZ'],
                    'gyroX': data['gyroX'],
                    'gyroY': data['gyroY'],
                    'gyroZ': data['gyroZ']
                })
        except:
            continue
    
    if not gps_points:
        return None, None, None
    
    first_ts = gps_points[0]['timestamp']
    filtered_imu = [d for d in imu_data if d['timestamp'] >= first_ts]
    
    print(f"GPS: {len(gps_points)}点, IMU: {len(filtered_imu)}点")
    return gps_points, filtered_imu

def interpolate_with_dead_reckoning(gps_points, imu_data, target_hz=100):
    """改进的航位推算算法"""
    if len(gps_points) < 2:
        return np.array([[p['lat'], p['lon']] for p in gps_points])
    
    gps_ts = np.array([p['timestamp'] for p in gps_points])
    gps_lat = np.array([p['lat'] for p in gps_points])
    gps_lon = np.array([p['lon'] for p in gps_points])
    gps_bearing = np.array([p.get('bearing', 0) for p in gps_points])
    
    base_lat = np.mean(gps_lat)
    meters_per_degree_lon = METERS_PER_DEGREE_LAT * math.cos(math.radians(base_lat))
    
    start_ts = gps_ts[0]
    end_ts = gps_ts[-1]
    new_ts = np.arange(start_ts, end_ts, 1.0 / target_hz)
    if len(new_ts) == 0 or new_ts[-1] < end_ts:
        new_ts = np.append(new_ts, end_ts)
    
    print(f"生成 {len(new_ts)} 个插值点...")
    
    # GPS线性插值作为基础轨迹
    interp_lat = interp1d(gps_ts, gps_lat, kind='linear', fill_value='extrapolate')
    interp_lon = interp1d(gps_ts, gps_lon, kind='linear', fill_value='extrapolate')
    base_lat_points = interp_lat(new_ts)
    base_lon_points = interp_lon(new_ts)
    
    if imu_data and len(imu_data) > 10:
        imu_ts = np.array([d['timestamp'] for d in imu_data])
        
        interp_gyro_z = interp1d(imu_ts, [d['gyroZ'] for d in imu_data], 
                                 kind='linear', fill_value='extrapolate', bounds_error=False)
        interp_accel_x = interp1d(imu_ts, [d['accelX'] for d in imu_data], 
                                  kind='linear', fill_value='extrapolate', bounds_error=False)
        interp_accel_y = interp1d(imu_ts, [d['accelY'] for d in imu_data], 
                                  kind='linear', fill_value='extrapolate', bounds_error=False)
        
        yaw = math.radians(gps_bearing[0]) if len(gps_bearing) > 0 and gps_bearing[0] is not None else 0.0
        
        v_north = 0.0
        v_east = 0.0
        
        lat_offset = np.zeros(len(new_ts))
        lon_offset = np.zeros(len(new_ts))
        
        print("积分IMU数据计算偏移...")
        for i in tqdm(range(len(new_ts) - 1), desc="IMU积分"):
            dt = new_ts[i+1] - new_ts[i]
            
            gz = interp_gyro_z(new_ts[i])
            ax_val = interp_accel_x(new_ts[i])
            ay_val = interp_accel_y(new_ts[i])
            
            # 更新航向角
            yaw += gz * dt
            yaw = (yaw + math.pi) % (2 * math.pi) - math.pi
            
            # 坐标系转换：设备→地理
            accel_north = ay_val * math.cos(yaw) - ax_val * math.sin(yaw)
            accel_east = ay_val * math.sin(yaw) + ax_val * math.cos(yaw)
            
            # 速度积分
            v_north += accel_north * dt
            v_east += accel_east * dt
            
            # 速度限制
            max_speed = 30.0
            speed = math.sqrt(v_north**2 + v_east**2)
            if speed > max_speed:
                scale = max_speed / speed
                v_north *= scale
                v_east *= scale
            
            # 计算位移（缩小IMU影响）
            imu_coefficient = 0.1
            delta_north_m = v_north * dt * imu_coefficient
            delta_east_m = v_east * dt * imu_coefficient
            
            # 转换为经纬度偏移
            lat_offset[i+1] = lat_offset[i] + delta_north_m / METERS_PER_DEGREE_LAT
            lon_offset[i+1] = lon_offset[i] + delta_east_m / meters_per_degree_lon
        
        # GPS点处强制偏移为0
        gps_indices = np.searchsorted(new_ts, gps_ts)
        gps_indices = np.clip(gps_indices, 0, len(new_ts) - 1)
        
        for i in range(len(gps_indices)):
            idx = gps_indices[i]
            lat_offset[idx] = 0
            lon_offset[idx] = 0
        
        # 高斯平滑
        sigma = 20
        gaussian_kernel = np.exp(-np.arange(-3*sigma, 3*sigma+1)**2/(2*sigma**2))
        gaussian_kernel /= gaussian_kernel.sum()
        
        lat_offset = np.convolve(lat_offset, gaussian_kernel, mode='same')
        lon_offset = np.convolve(lon_offset, gaussian_kernel, mode='same')
        
        # 叠加偏移
        final_lat = base_lat_points + lat_offset
        final_lon = base_lon_points + lon_offset
        
        return np.column_stack((final_lat, final_lon)), new_ts
    
    return np.column_stack((base_lat_points, base_lon_points)), new_ts

def calculate_speeds(points, timestamps):
    """计算各点速度"""
    if len(points) < 2:
        return []
    
    speeds = []
    for i in range(1, len(points)):
        lat1, lon1 = points[i-1]
        lat2, lon2 = points[i]
        
        dlat = lat2 - lat1
        dlon = lon2 - lon1
        avg_lat = (lat1 + lat2) / 2
        distance = math.sqrt((dlat * METERS_PER_DEGREE_LAT) ** 2 + 
                           (dlon * METERS_PER_DEGREE_LAT * math.cos(math.radians(avg_lat))) ** 2)
        
        dt = timestamps[i] - timestamps[i-1] if i < len(timestamps) else 0.01
        speed = distance / max(dt, 0.001)
        speeds.append(speed)
    
    speeds.insert(0, speeds[0] if speeds else 0)
    return speeds

def get_color(speed, max_speed):
    """根据速度获取颜色"""
    ratio = min(1.0, max(0.0, speed / max(max_speed, 1)))
    return (ratio, 1 - ratio, 0)

def export_to_gpx(points, timestamps, gps_points, file_name):
    """导出GPX文件"""
    print(f"导出GPX文件: {file_name}")
    
    if not gps_points or len(gps_points) < 1:
        print("无GPS数据，跳过GPX导出")
        return
    
    epoch_start = datetime(1970, 1, 1)
    
    gpx_content = []
    gpx_content.append('<?xml version="1.0" encoding="UTF-8"?>')
    gpx_content.append('<gpx version="1.1" creator="KartMotionTrack"')
    gpx_content.append('     xmlns="http://www.topografix.com/GPX/1/1"')
    gpx_content.append('     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"')
    gpx_content.append('     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">')
    gpx_content.append('  <metadata>')
    gpx_content.append(f'    <name>{os.path.splitext(file_name)[0]}</name>')
    gpx_content.append(f'    <desc>Kart Motion Track with Dead Reckoning at 100Hz</desc>')
    gpx_content.append(f'    <time>{datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")}</time>')
    gpx_content.append('  </metadata>')
    gpx_content.append('  <trk>')
    gpx_content.append(f'    <name>Kart Track</name>')
    gpx_content.append('    <trkseg>')
    
    for i in range(len(points)):
        lat, lon = points[i]
        ts = timestamps[i]
        
        point_time = epoch_start + timedelta(seconds=ts)
        time_str = point_time.strftime("%Y-%m-%dT%H:%M:%S")
        milliseconds = int((ts % 1) * 1000)
        time_with_ms = f"{time_str}.{milliseconds:03d}Z"
        
        gpx_content.append(f'      <trkpt lat="{lat:.10f}" lon="{lon:.10f}">')
        gpx_content.append(f'        <time>{time_with_ms}</time>')
        
        if i < len(gps_points):
            alt = gps_points[i].get('alt', 0)
            gpx_content.append(f'        <ele>{alt:.2f}</ele>')
        
        gpx_content.append('      </trkpt>')
    
    gpx_content.append('    </trkseg>')
    gpx_content.append('  </trk>')
    gpx_content.append('</gpx>')
    
    with open(file_name, 'w', encoding='utf-8') as f:
        f.write('\n'.join(gpx_content))
    
    print(f"GPX导出完成: {file_name}")

def plot_track(original_points, imu_points, speeds, file_name):
    """绘制轨迹图"""
    min_lat = min(original_points[:, 0].min(), imu_points[:, 0].min())
    max_lat = max(original_points[:, 0].max(), imu_points[:, 0].max())
    min_lon = min(original_points[:, 1].min(), imu_points[:, 1].min())
    max_lon = max(original_points[:, 1].max(), imu_points[:, 1].max())
    
    pad = 0.003
    min_lat -= pad
    max_lat += pad
    min_lon -= pad
    max_lon += pad
    
    aspect = (max_lon - min_lon) / (max_lat - min_lat)
    height = 12
    width = height * aspect
    
    plt.figure(figsize=(width, height), dpi=100)
    ax = plt.gca()
    
    diagonal = math.sqrt((max_lat-min_lat)**2 + (max_lon-min_lon)**2)
    zoom = 18 if diagonal <= 0.005 else 17
    print(f"缩放级别: {zoom}")
    
    # 先绘制地图背景（zorder=0）
    plot_map_background(ax, min_lat, max_lat, min_lon, max_lon, zoom)
    
    max_speed = max(speeds) if speeds else 1
    print(f"最高速度: {max_speed:.2f} m/s")
    
    # 绘制IMU轨迹（zorder=1）
    print("绘制轨迹...")
    for i in range(len(imu_points) - 1):
        speed = speeds[i] if i < len(speeds) else 0
        color = get_color(speed, max_speed)
        ax.plot([imu_points[i, 1], imu_points[i+1, 1]], 
                [imu_points[i, 0], imu_points[i+1, 0]], 
                color=color, linewidth=2, zorder=1)
    
    # 绘制GPS点和起点终点（zorder=2）
    ax.plot(original_points[0, 1], original_points[0, 0], 'go', markersize=10, label='起点', zorder=2)
    ax.plot(original_points[-1, 1], original_points[-1, 0], 'ro', markersize=10, label='终点', zorder=2)
    ax.plot(original_points[:, 1], original_points[:, 0], 'b.', markersize=6, alpha=0.8, label='GPS点', zorder=2)
    
    plt.title(f'{os.path.splitext(file_name)[0]}_IMU\n航位推算轨迹', fontsize=14)
    plt.legend(loc='upper right')
    ax.set_aspect('equal', adjustable='box')
    ax.set_xlim(min_lon, max_lon)
    ax.set_ylim(min_lat, max_lat)
    plt.tight_layout()
    
    output = os.path.splitext(file_name)[0] + '_IMU_track.png'
    plt.savefig(output, dpi=100, bbox_inches='tight')
    plt.close()
    print(f"已保存: {output}")

def main():
    raw_files = [f for f in os.listdir('.') if f.lower().endswith('.json') and 'raw' in f.lower()]
    
    if not raw_files:
        print("未找到raw_track文件")
        return
    
    for raw_file in raw_files:
        print(f"\n{'='*70}")
        print(f"处理文件: {raw_file}")
        print('='*70)
        
        gps_points, imu_data = read_raw_track(raw_file)
        if gps_points is None or len(gps_points) < 2:
            continue
        
        gps_array = np.array([[p['lat'], p['lon']] for p in gps_points])
        timestamps = np.array([p['timestamp'] for p in gps_points])
        
        print("\n执行航位推算...")
        imu_points, new_timestamps = interpolate_with_dead_reckoning(gps_points, imu_data, target_hz=100)
        
        print("\n计算速度...")
        speeds = calculate_speeds(imu_points, new_timestamps)
        
        gpx_file = os.path.splitext(raw_file)[0] + '_IMU.gpx'
        export_to_gpx(imu_points, new_timestamps, gps_points, gpx_file)
        
        print("\n生成轨迹图...")
        plot_track(gps_array, imu_points, speeds, raw_file)
        
        print(f"\n{'='*70}")
        print(f"处理完成: {raw_file}")
        print('='*70)

if __name__ == "__main__":
    main()
