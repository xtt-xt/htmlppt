# 保留 JS 桥接接口的方法名，避免混淆后 JS 调用不到
-keepclassmembers class com.example.htmlppt.JsBridge {
    public *;
}