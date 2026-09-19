# ntop: Shizuku user service + AIDL must survive R8 (looked up by
# class name from the Shizuku server process; explicit tag used too).
-keep class com.ntop.app.stats.ProcUserService { *; }
-keep class com.ntop.app.stats.IProcService* { *; }
