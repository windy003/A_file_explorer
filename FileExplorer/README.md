# Android 文件浏览器

一个简单而功能强大的Android文件浏览器应用，支持浏览、打开、重命名和删除文件。

## 功能特点

- 📁 浏览设备存储中的文件和文件夹
- 🔍 清晰的文件和文件夹图标区分
- 📱 现代化的Material Design界面
- 🗂️ 支持文件操作：打开、重命名、删除
- 📊 显示文件大小和修改时间
- 🔙 支持返回上级目录
- 🔒 处理Android存储权限

## 系统要求

- Android 7.0 (API 24) 及以上版本
- 存储权限（应用会自动请求）

## 安装和运行

1. 使用Android Studio打开项目
2. 连接Android设备或启动模拟器
3. 运行应用

## 权限说明

应用需要以下权限：
- `READ_EXTERNAL_STORAGE` - 读取存储文件
- `WRITE_EXTERNAL_STORAGE` - 修改和删除文件
- `MANAGE_EXTERNAL_STORAGE` - Android 11+ 完整文件管理权限

## 使用说明

1. **浏览文件**：点击文件夹进入，点击文件尝试打开
2. **返回上级**：点击工具栏的返回按钮或使用系统返回键
3. **文件操作**：点击文件/文件夹右侧的三点菜单
   - 打开：打开文件或进入文件夹
   - 重命名：修改文件/文件夹名称
   - 删除：删除文件/文件夹

## 项目结构

```
app/
├── src/main/
│   ├── java/com/example/fileexplorer/
│   │   ├── MainActivity.kt          # 主活动
│   │   ├── FileAdapter.kt          # 文件列表适配器
│   │   └── FileItem.kt             # 文件数据模型
│   ├── res/
│   │   ├── layout/                 # 布局文件
│   │   ├── drawable/               # 图标资源
│   │   ├── values/                 # 字符串、颜色等资源
│   │   └── menu/                   # 菜单资源
│   └── AndroidManifest.xml         # 应用清单
└── build.gradle                    # 构建配置
```

## 技术特性

- **Kotlin**: 现代Android开发语言
- **Material Design**: 遵循Google设计规范
- **RecyclerView**: 高效的列表显示
- **FileProvider**: 安全的文件共享
- **权限管理**: 处理各版本Android的存储权限

## 后续改进

- [ ] 搜索功能
- [ ] 文件复制/移动
- [ ] 多选操作
- [ ] 不同文件类型的预览
- [ ] 文件排序选项
- [ ] 收藏夹功能