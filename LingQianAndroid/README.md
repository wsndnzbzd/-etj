# 灵签 Android 项目（原型 0.1.0）

这是一个离线运行的 Android / Kotlin / Jetpack Compose 项目，包含：
- 观音百签（浅草寺系汉诗百签）及本地解读数据
- 月老灵签 101 条数据及分类解读
- 摇手机触发抽签的体感交互
- 两枚虚拟杯筊、三种结果及连续三次圣杯确认
- 深红与鎏金风格界面
- 不登录、不联网、不保存求签历史

签诗版本存在地域和流派差异。月老签及现代分类解读属于个人体验用途的整理，不代表所有寺庙统一版本，也不是对现实事件的可靠预测。数据来源与限制见 `DATA_SOURCES.md`。

## 不安装 Android Studio，在线编译 APK

项目包含 GitHub Actions 自动构建配置：`.github/workflows/build-apk.yml`。把项目上传到自己的 GitHub 仓库后，GitHub 会在推送到 `main`/`master` 分支时自动编译；也可以在仓库的 **Actions → Build Android APK → Run workflow** 手动触发。

构建成功后：
1. 打开仓库的 **Actions** 页面。
2. 进入最新一次 **Build Android APK** 运行记录。
3. 在页面底部的 **Artifacts** 下载 `LingQian-debug-apk`。
4. 解压下载的 ZIP，里面的 `app-debug.apk` 就是安卓安装包。
5. 把 APK 传到手机并点击安装。Android 可能会要求允许当前来源安装未知应用。

## 上传步骤

1. 登录 GitHub，创建一个新的空仓库，例如 `lingqian-android`。
2. 进入仓库，选择 **Add file → Upload files**。
3. 先在电脑上解压本项目 ZIP，进入里面的 `LingQianAndroid` 文件夹，把该文件夹里的全部文件和 `.github` 文件夹内容上传到仓库根目录。不要把外层 ZIP 本身上传后就结束。
4. 点击 **Commit changes**。进入 **Actions** 查看自动构建。

如果 GitHub 提示需要启用 Actions，在仓库 **Actions** 页启用即可。无需提供 GitHub 密码、访问令牌或其他账号凭据给任何人。

## 本地构建（可选）

需要 Android SDK、JDK 17 和 Gradle 8.9。执行：

```bash
gradle --no-daemon assembleDebug
```

成功后 APK 通常位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 体感操作

- 观音签 / 月老签：完成静心后，握稳手机左右摇动数次并停下，传感器识别动作后触发抽签。
- 掷圣杯：摇晃手机两三次，再快速向前轻推并停住，识别动作后播放杯筊翻滚动画。
- 使用 Android `SensorManager` 读取加速度计和陀螺仪。传感器阈值为启发式设置，不同手机需要真机调试；手动按钮作为备用。
- 传感器只触发交互，签文和杯筊结果由本地随机逻辑生成。请握稳手机，不要真的把手机抛出去。

## 注意

本项目当前是未经过真机验证的开发版本。云端构建成功后仍建议先在自己的手机上测试体感灵敏度、动画和安装兼容性。它不需要任何账号或个人数据，也不申请网络权限。
