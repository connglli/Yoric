# Yoric

> How TIM escape being killed in Andorid 10? (based on [Leoric](https://github.com/tiann/Leoric.git))

## How force-stop worked?

The workflow:
``` 
1. Parse pacakge name of the app to get its UID, say UIDXXX 
   (ActivityManagerService#forceStopPackage())
2. Collect into procs (ArrayList<ProcessRecord>) all processes whose UID is UIDXXX 
   via iterating the ProcessList maintained by ActivityManagerService 
   (ProcessList#killPackageProcessesLocked())
3. For each process P in procs (ProcessList#killPackageProcessesLocked()): 
  2.1 kill P using syscall kill(<pid_of_P>, SIGKILL) (ProcessRecord#kill() 
      -> Process#killProcessQuiet())
  2.2 kill the process members of the process cgroup P resides in (ProcessRecord#kill() 
      -> ProcessList#killProcessGroup() -> Process#killProcessGroup() 
      -> libprocessgroup: KillProcessGroup())
    2.2.1 read /proc/<pid_of_p>/cgroup to know which process cgroup p belongs to, 
          say C (libprocessgroup: KillProcessGroup())
    2.2.2 for each process PP in the cgroup C (by reading /acct/uid_UIDXXX/pid_<pid_of_C>/cgroup.procs):
      a. if PP is a leader of a process group (PID == PGID), kill its process group using 
         syscall kill(-<pid_of_PP>, SIGKILL)
      b. otherwise, kill PP itself (libprocessgroup: DoKillProcessGroupOnce())
    2.2.3 repeat 2.2.2 40 times, and sleep 5ms each time each after
4. clear other components still managered by other managers
```

## What are Yoric processes?

Yoric is a multi-proc-arch app, with the following processes:
+ `main`: `com.example.yoric`, used by `com.example.yoric.MainActivity`
+ `remote`: `com.example.yoric:remote`, used by `com.example.yoric.RemoteService`
+ `android-remote`: `android.remote`, used by `com.example.yoric.AndroidRemoteService`
+ `remote-c`: `com.example.yoric:remote-c`, forked twice using JNI by `remote`, and left as an orphan process
+ `android-remote-c`: `android.remote-c`, forked twice using JNI by `android-remote`, and left as an orphan process

When Yoric is installed, and started, one can use `ps -o USER,PID,PGID,PPID,NAME` to get the following information:
```
USER         PID    PGID  PPID NAME
root         1      0     0    init
root         1543   1543  1    zygote
u0_a129      23574  1543  1543 com.example.yoric
u0_a129      23603  1543  1543 com.example.yoric:remote
u0_a129      23631  1543  1    com.example.yoric:remote-c
u0_a129      23625  1543  1543 android.remote
u0_a129      23658  1543  1    android.remote-c
```

From where, one can see that:
+ UID (USER): all same, indicating all processes of Yoric share the same UID, which is the UID of Yoric, i.e., 10129 (u0_a129) (see it using `adb shell cat /data/system/packages.xml | grep com.example.yoric`)
+ PGID: all same and equals to PID of zygote, indicating all processes of Yoric forked directly or indirectly by zygote belongs to the process group led by zygote

Given that Yoric is a general and default-setting (empty activitiy/service implementations) 3rd-party demo app, one concludes that 
```
in Android, almoast all processes forked directly or indirectly by zygote belongs to 
the process group led by zygote (unless the app itself sets the process group manually) 
```

## Intuitively, what does force-stop do?

Given the abvoe conclusion, one knows in practice, when force-stopping a common 3rd-party app, the workflow `2.2.2-a` is usually *redandant*, and no process groups are actually killed (cause almost all processes are members of zygote group, not leaders), unless the app itself sets the process groups manually

Thereby intuitively, force-stop kills all processes *one by one* (not groupfully) sharing the same UID as the app to be killed

## How Yoric work?

As one knows, force-stop kills the processes by firstly collecting all processes that to be killed, then kill them one by one, so if a process is spawn when another process is killed, it is not contained by the collected process list, and thereby escaped killing once.

Then how to know one process is killed, the magic behind is `flock()`, i.e., two processes watches each other by locking the other's file (called them *watcher* and *watchee*).

In general, the watcher (`remote`, `remote-c`, `andorid-remote`, and `android-remote-c`) creates and locks a file (call it indicator), and watches the watchee by trying to lock its indicator, so if watchee is still alive, considering its indicator is already locked, watcher locks failed, othewise watcher knows that watchee died, then restarts it and kills self, making all happen from scratch.

Considering that the indicator creation and lock of one process should be completed atomically (e.g., if watcher locks the indicator of watchee right after the it is created but before locked by watchee, then watcher locks successfully, which makes it incorrectly treat watchee as dead, however, the watchee is still alive), the other process have to wait before lock succeeded. To implement this, watchee creats a flag file after successful lock, and watcher tests the existence of the flag file, if the flag exists, it knows watchee locked successfully, afterwards, it helps watchee to delete the flag (*IMPORTANT! IF WATCHER DOES NOT HELP WATCHEE DELETE IT, WHEN WATCHEE IS KILLED, THE FLAG STILL EXISTS, WHICH BREAKS THE ATOMIC CHARACTICS, AND IN THE END LEAD WATCHER TO INCORRECTLY CONSIDERING WATCHEE AS DEAD*).

In detail, 
+ `remote` and `android-remote` watches each other
+ `remote-c` and `android-remote-c` watches each other

## What is the Yoric workflow?

```
1. com.example.yoric (MainActivity) starts, and also starts com.example.yoric:remote (RemoteService)
2. com.example.yoric:remote starts android.remote (AndroidRemostService)
3. com.example.yoric:remote forks twice to com.example.yoric:remote-c
4. android.remote forks twice to android.remote-c
5. each watcher creates and locks self's file
6. aftewards, creates a flag file to tell its watcher that it is ready
7. aftewards, waits until it watchee ready
8. aftewards, watches its watchee 
9. when one process is watched dead, the watcher starts it, and kill self
```

## References

0. [weishu - Leoric](https://github.com/tiann/Leoric.git)
1. [weishu - Android 黑科技保活实现原理揭秘](http://weishu.me/2020/01/16/a-keep-alive-method-on-android/)
2. [gityuan - 深度剖析APP保活案例](http://gityuan.com/2018/02/24/process-keep-forever/)
3. [ActivityManagerService#forceStopPackage()](http://www.aospxref.com/android-10.0.0_r2/xref/frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java#4258)
4. [ProcessList#killPackageProcessesLocked()](http://www.aospxref.com/android-10.0.0_r2/xref/frameworks/base/services/core/java/com/android/server/am/ProcessList.java#2116)
5. [ProcessRecord#kill()](http://www.aospxref.com/android-10.0.0_r2/xref/frameworks/base/services/core/java/com/android/server/am/ProcessRecord.java#759)
6. [libprocessgroup: KillProcessGroup()](http://www.aospxref.com/android-10.0.0_r2/xref/system/core/libprocessgroup/processgroup.cpp#340)
7. [libprocessgroup: DoKillProcessGroupOnce()](http://www.aospxref.com/android-10.0.0_r2/xref/system/core/libprocessgroup/processgroup.cpp#262)