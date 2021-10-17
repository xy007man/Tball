#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <functional>
#include <android/log.h>

#define  LOG_TAG    "Billiards_JNI_LOG"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;
using namespace std;

double distance(Point p1, Point p2)
{
    return sqrt(pow(abs(p1.x - p2.x), 2) + pow(abs(p1.y - p2.y), 2));
}

vector<Point> findLine(Mat &img, Point center, int minLimit, int maxLimit)
{
    vector<Point> res;
    int sumX = 0;
    int sumY = 0;
    int cnt = 0;

    function<void (Point)> dfs = [&](Point p) {

        if (distance(p, center) < minLimit || distance(p, center) > maxLimit) {
            return;
        }

        int direct[][2] = {{0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}};

        int b = img.at<Vec3b>(p.y, p.x)[0]; // blue
        int g = img.at<Vec3b>(p.y, p.x)[1]; // green
        int r = img.at<Vec3b>(p.y, p.x)[2]; // red

        if (255 - b > 30 || 255 - g > 30 || 255 - r > 30) {
            return;
        }

        img.at<Vec3b>(p.y, p.x)[0] = 0;
        img.at<Vec3b>(p.y, p.x)[1] = 0;
        img.at<Vec3b>(p.y, p.x)[2] = 255;

        sumX += p.x;
        sumY += p.y;
        cnt++;

        for (int i = 0; i < 8; i++) {
            dfs(Point(p.x + direct[i][0], p.y + direct[i][1]));
        }
    };

    for (int row = center.y - maxLimit; row < center.y + maxLimit; row++) {
        for (int col = center.x - maxLimit; col < center.x + maxLimit; col++) {

            int b = img.at<Vec3b>(row, col)[0]; // blue
            int g = img.at<Vec3b>(row, col)[1]; // green
            int r = img.at<Vec3b>(row, col)[2]; // red

            // 白色位置
            if (255 - b > 35 || 255 - g > 35 || 255 - r > 35) {
                continue;
            }

            dfs(Point(col, row)); // 传入的是坐标， col 是 x，row 是 y

            // 防止有其他白色像素的干扰
            if (cnt > 20) {
                res.push_back(Point(round((double)sumX / (double)cnt),
                                    round((double)sumY / (double)cnt)));
            }

            sumX = 0;
            sumY = 0;
            cnt = 0;
        }
    }

    return res;
}

Point getCircleCenterPostion(const Mat &srcImage, const Mat &templateImage, double &val)
{
    Mat grayImage, grayTemplateImage, res;
    double minVal, maxVal;
    Point minLoc, maxLoc;

    //将图像转换为灰度图
    cvtColor(srcImage, grayImage, COLOR_RGB2GRAY);
    cvtColor(templateImage, grayTemplateImage, COLOR_RGB2GRAY);

    matchTemplate(grayImage, grayTemplateImage, res, TM_CCOEFF_NORMED);
    minMaxLoc(res, &minVal, &maxVal, &minLoc, &maxLoc);  //  提取极值
    val = maxVal;

    return maxLoc;
}

Point getEndPostion(const Point &circleCenter, const Point &point, int w, int h)
{
    // 和 y 轴 平行
    if (point.x == circleCenter.x) {
        if (point.y < circleCenter.y) {
            return Point(circleCenter.x, 0);
        }
        return Point(circleCenter.x, h);
    }

    // y = kx + b
    int x = 0;
    double k = ((double)point.y - (double)circleCenter.y) / ((double)point.x - (double)circleCenter.x);
    double b = (double)circleCenter.y - k * (double)circleCenter.x;

    if (point.y < circleCenter.y) {
        x = round(-b / k);
        if (x > w) {
            return Point(w, round(k * w + b));
        }
        if (x < 0) {
            return Point(0, round(b));
        }
        return Point(x, 0);
    }

    x = round(((double)h - b) / k);
    if (x > w) {
        return Point(w, round(k * w + b));
    }
    if (x < 0) {
        return Point(0, round(b));
    }

    return Point(x, h);
}

jobjectArray genArray(JNIEnv *env, const Point &circleCenter, const vector<Point> &endPostions)
{
    jclass pointClass = env->FindClass("android/graphics/Point");
    jfieldID x = env->GetFieldID(pointClass,"x","I");
    jfieldID y = env->GetFieldID(pointClass,"y","I");
    jobject pointObject = env->AllocObject(pointClass);
    // 圆心坐标
    env->SetIntField(pointObject, x, circleCenter.x);
    env->SetIntField(pointObject, y, circleCenter.y);

    jobjectArray array = env->NewObjectArray(endPostions.size() + 1, pointClass, 0);
    env->SetObjectArrayElement(array, 0, pointObject);

    for (int i = 0; i < endPostions.size(); i++) {
        pointObject = env->AllocObject(pointClass);
        env->SetIntField(pointObject, x, endPostions[i].x);
        env->SetIntField(pointObject, y, endPostions[i].y);
        env->SetObjectArrayElement(array, i + 1, pointObject);
    }

    return array;
}

extern "C" JNIEXPORT jobjectArray  JNICALL
Java_pers_xiaoyu_billiards_utils_BilliardsUtils_getGuidePostion(
        JNIEnv* env,
        jclass,
        jstring screenPath, jstring templatePath) {

    // 读取图像
    Mat srcImage = imread(env->GetStringUTFChars(screenPath, 0));
    Mat templateImage = imread(env->GetStringUTFChars(templatePath, 0));
    if (srcImage.empty() || templateImage.empty()) {
        LOGE("read image failed, srcImage %d:%s, templateImage %d:%s",
             srcImage.empty(), env->GetStringUTFChars(screenPath, 0),
             templateImage.empty(), env->GetStringUTFChars(templatePath, 0));
        return nullptr;
    }

    // 查找最佳图像最佳匹配位置
    double val;
    Point maxLoc = getCircleCenterPostion(srcImage, templateImage, val);
    if (val < 0.7) {
        LOGE("matchTemplate failed val %f", val);
        return nullptr; // 匹配度未达到临界值
    }
    LOGI("matchTemplate success %f", val);

    // 确定圆心
    Point circleCenter(maxLoc.x + templateImage.cols / 2, maxLoc.y + templateImage.rows / 2);

    rectangle(srcImage, Point(maxLoc), Point(maxLoc.x + templateImage.cols, maxLoc.y + templateImage.rows), Scalar(0, 0, 255), 1); //  绘制得分最高结果的矩形边界
    circle(srcImage, circleCenter, templateImage.cols / 2 * 3, Scalar(0, 0, 255), 1);
    circle(srcImage, circleCenter, templateImage.cols / 2 * 3 + 15, Scalar(0, 0, 255), 1);

    // 深度优先查找辅助线（白色区域）
    vector<Point> points = findLine(srcImage, circleCenter, templateImage.cols / 2 * 3, templateImage.cols / 2 * 3 + 15);

    // 获取辅助线的终点坐标
    vector<Point> endPostions;
    for (int i = 0; i < points.size(); i++) {
        Point endPostion = getEndPostion(circleCenter, points[i], srcImage.cols, srcImage.rows);
        line(srcImage, circleCenter, endPostion, Scalar(0, 0, 255), 3);
        endPostions.push_back(endPostion);
    }

    //imwrite("/sdcard/Pictures/res.jpg", srcImage);

    // 生成 jobjectArray 返回给 java 侧
    return genArray(env, circleCenter, endPostions);
}

