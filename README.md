​	最近在玩了一下微信小游戏中的桌球，游戏中自带的瞄准线太短了，就想找一个辅助器。在GitHub上搜索了半天没有发现特别好用的，GitHub上一般都是基于PC版的或者是需要用adb连接PC（晕，我玩个手机游戏还需要连着电脑？）。于是打算自己动手做一个。

## 基本思路

* 确定瞄准圆环的位置：将圆环的图片用抠图工具截取出来，使用opencv中的`matchTemplate`进行图像匹配（图中矩形）

* 确定瞄准线的坐标：使用深度优先算法，搜索瞄准圆环周边的白色像素坐标，然后取平均值，得到瞄准线的坐标（在图中两个圆环中间区域中搜索白色像素）

* 画出瞄准线：连接第一步确定的瞄准圆环圆心和第二步确定的瞄准线坐标画出瞄准线，另外需要注意的是我们画的是射线，需要通过这两点的相对位置，来确定射线的方向

* 在手机屏幕中显示出辅助线：利用android的的护眼模式的原理，创建一个透明的图层，覆盖在游戏上，绘制这个透明图层就能看到辅助线

  ![res](https://github.com/xy007man/Tball/blob/main/other/res.jpg)

## 工程目录结构

* app主要负责屏幕截图、透明图层创建、绘制辅助线（本人不太了解android app开发，所以这部分代码只是勉强能用哈）
* Billiards_SDK是JNI层，主要是进行瞄准圆环的匹配，确定辅助线坐标

![image-20211017153332240](https://github.com/xy007man/Tball/blob/main/other/image-20211017153332240.png)

---

## 辅助工具的使用

* 使用android studio编译工程，将生成的apk文件push到手机中安装（第一次安装时需要悬浮权限、读取sdcard权限及录屏权限，依次允许就行）
*  将`Tball\other\circle.jpg`图像文件push到手机`/sdcard/Pictures`文件夹下，用于匹配瞄准环（我的手机屏幕分辨率是1080x2400，所以不同分辨率的手机需要自行截取圆环图像进行替换）
* 打开Tball apk后，分别点击`启动Service`、`初始化服务`，然后将apk切换到后台，之后正常启动游戏即可（有的手机可能会误杀后台进程造成辅助失效，需要手动设置一下电源管理）

---

## 本地运行环境

* 手机：荣耀V30 pro
* android 版本：Android 10

---

## 项目GitHub链接

* https://github.com/xy007man/Tball

---

## 鸣谢（排名不分先后~）

* Android权限框架：https://github.com/getActivity/XXPermissions

* MediaProjection 截屏、录屏Demo：https://github.com/jiashuaishuai/MediaProjectionDemo

* 自定义View之自动刷新View：https://blog.csdn.net/qq_16519957/article/details/88768025

* Android如何实现全局的护眼模式：https://blog.csdn.net/weixin_42433094/article/details/119137569

* 腾讯桌球助手：https://github.com/CSUFT-Running-Bug/billiard-assistant
