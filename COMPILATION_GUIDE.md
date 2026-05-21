# 编译指南 - 在 Android Studio 中编译和运行

## 📋 前提条件

在开始之前，请确保已安装：

1. **Android Studio** (Hedgehog 2023.1.1 或更高版本)
   - 下载地址：https://developer.android.com/studio
   - 安装时选择包含 Android SDK 的版本

2. **JDK 17+** (Android Studio 通常自带)

3. **真实 Android 设备** (推荐) 或 Android 模拟器
   - 真实设备需要：加速度计、陀螺仪、GPS
   - 模拟器可能无法完全测试传感器功能

## 🚀 编译步骤

### 方法一：使用 Android Studio 图形界面（推荐）

1. **打开项目**
   - 启动 Android Studio
   - 选择 "Open an existing project"
   - 导航到 `d:\AIProjects\KartMotionTrack`
   - 点击 "OK"

2. **等待 Gradle 同步**
   - 首次打开时，Android Studio 会自动下载 Gradle 和依赖
   - 在底部状态栏可以看到进度
   - 可能需要几分钟时间
   - 如果遇到同步错误，查看下方"常见问题"部分

3. **连接 Android 设备**
   - 使用 USB 数据线连接手机
   - 在手机上开启"开发者选项"和"USB 调试"
   - 手机上会弹出"允许 USB 调试"对话框，点击"允许"
   - Android Studio 应该能识别到设备

4. **编译并运行**
   - 点击工具栏的绿色三角形（Run 按钮）
   - 或使用快捷键 `Shift + F10`
   - 选择目标设备
   - 点击 "OK"

5. **等待安装**
   - Gradle 会编译项目（首次编译需要几分钟）
   - 编译完成后会自动安装到设备
   - 应用会自动启动

### 方法二：使用命令行编译

如果你更喜欢命令行：

1. **打开终端**
   - Windows: 打开 PowerShell 或 CMD
   - 导航到项目目录：
     ```bash
     cd d:\AIProjects\KartMotionTrack
     ```

2. **检查 Gradle Wrapper**
   - 项目已包含 gradle-wrapper.properties
   - 不需要单独安装 Gradle

3. **编译 Debug 版本**
   ```bash
   .\gradlew assembleDebug
   ```

4. **安装到设备**
   ```bash
   .\gradlew installDebug
   ```

5. **启动应用**
   ```bash
   .\gradlew run
   ```

## 📱 使用应用

1. **首次启动**
   - 应用会请求权限
   - 点击"允许"授予位置权限
   - 应用还需要存储权限（用于保存轨迹数据）

2. **等待 GPS 定位**
   - 初次使用需要等待 GPS 信号
   - 状态栏会显示"等待GPS信号..."

3. **开始追踪**
   - 点击"开始"按钮
   - 状态栏会显示"追踪已启动"
   - 可以把手机放到裤兜里或支架上

4. **结束追踪**
   - 点击"停止"按钮
   - 状态栏会显示记录的数据点数量

5. **查看数据**
   - 数据保存在：`/Android/data/com.karttracker/files/`
   - 文件名为：`kart_track_YYYYMMDD_HHmmss.json`
   - 可以用文件管理器或 ADB pull 导出

## 🔧 常见问题

### Q1: Gradle 同步失败
**解决方法：**
- 检查网络连接
- 点击 "File" → "Invalidate Caches" → "Invalidate and Restart"
- 删除 `.gradle` 和 `build` 文件夹后重试

### Q2: 无法识别 Android 设备
**解决方法：**
- 确保手机上已开启"开发者选项"
- 确保已开启"USB 调试"
- 尝试更换 USB 数据线（有些数据线只充电不支持数据）
- 在设备管理器中查看是否有未识别的设备

### Q3: 编译报错 "SDK location not found"
**解决方法：**
- 点击 "File" → "Project Structure"
- 在 "SDK Location" 中指定 Android SDK 路径
- 或在 `local.properties` 文件中添加：
  ```
  sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
  ```

### Q4: 应用安装失败
**解决方法：**
- 检查手机存储空间是否足够
- 卸载旧版本应用
- 检查 USB 调试是否真的连接成功

## 📊 测试建议

### 静态测试（第一阶段）
1. 手机放在桌子上
2. 启动应用，观察姿态输出
3. 旋转手机，观察姿态变化
4. 线性加速度应该接近 0

### 步行测试（第二阶段）
1. 手持手机直线步行 100 米
2. 观察轨迹是否直线
3. 对比 GPS 轨迹

### 卡丁车测试（第三阶段）
1. 手机放裤兜或支架
2. 跑几圈赛道
3. 分析轨迹数据
4. 对比真实赛道

## 🎯 验证清单

编译成功后，检查以下功能：

- [ ] 应用成功安装到设备
- [ ] 位置权限已授予
- [ ] 状态栏显示"等待GPS信号..."
- [ ] GPS 信号获取后显示经纬度
- [ ] 点击"开始"按钮
- [ ] 实时显示速度和姿态
- [ ] 点击"停止"按钮
- [ ] 查看保存的 JSON 文件
- [ ] JSON 文件格式正确

## 🐛 调试技巧

如果发现问题：

1. **查看 Logcat**
   - 在 Android Studio 底部点击 "Logcat"
   - 选择设备进程
   - 过滤 "KartTracker" 或 "com.karttracker"

2. **常用调试命令**
   ```bash
   # 查看实时日志
   adb logcat -c && adb logcat | grep KartTracker
   
   # 导出轨迹文件
   adb pull /Android/data/com.karttracker/files/ .
   ```

3. **数据验证**
   - 用 Python 或其他工具读取 JSON 文件
   - 检查时间戳是否连续
   - 检查经纬度是否合理

## 📞 获取帮助

如果遇到问题：
1. 查看 Logcat 中的错误信息
2. 对比本文档的"常见问题"部分
3. 检查代码实现（参考 kart-tracker-logic.md）

---

**祝测试顺利！** 🎉
