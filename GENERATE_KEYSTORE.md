# 生成 Debug Keystore

Android Studio 会自动在第一次编译 debug 版本时生成 `~/.android/debug.keystore`。

如果本地编译 release 版本时遇到 "Keystore not found" 错误，可以手动生成：

```bash
mkdir -p ~/.android
keytool -genkeypair -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"
```

或者直接用 Android Studio 先编译一次 debug 版本（会自动生成）。

## 签名说明

- **本地签名**：使用 `~/.android/debug.keystore`
- **CI 签名**：GitHub Actions 会自动生成相同的 keystore
- **签名一致性**：本地和 CI 使用相同的 debug keystore 配置
