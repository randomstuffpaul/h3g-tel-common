package com.android.internal.telephony;

import android.app.ActivityManagerNative;
import android.app.IActivityController;
import android.app.IActivityManager;
import android.app.IActivityManager.WaitResult;
import android.app.IInstrumentationWatcher.Stub;
import android.app.ProfilerInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Telephony.BaseMmsColumns;
import android.provider.Telephony.Sms.Intents;
import android.util.AndroidException;
import android.view.IWindowManager;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class Am {
    private static final String FATAL_ERROR_CODE = "Error type 1";
    private static final String NO_CLASS_ERROR_CODE = "Error type 3";
    private static final String NO_SYSTEM_ERROR_CODE = "Error type 2";
    private IActivityManager mAm;
    private String[] mArgs;
    private boolean mAutoStop;
    private String mCurArgData;
    private int mNextArg;
    private String mProfileFile;
    private String mReceiverPermission;
    private int mRepeat = 0;
    private int mSamplingInterval;
    private int mStartFlags = 0;
    private boolean mStopOption = false;
    private int mUserId;
    private boolean mWaitOption = false;

    private class InstrumentationWatcher extends Stub {
        private boolean mFinished;
        private boolean mRawMode;

        private InstrumentationWatcher() {
            this.mFinished = false;
            this.mRawMode = false;
        }

        public void setRawOutput(boolean rawMode) {
            this.mRawMode = rawMode;
        }

        public void instrumentationStatus(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                String pretty = null;
                if (!(this.mRawMode || results == null)) {
                    pretty = results.getString("stream");
                }
                if (pretty != null) {
                    System.out.print(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println("INSTRUMENTATION_STATUS: " + key + "=" + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_STATUS_CODE: " + resultCode);
                }
                notifyAll();
            }
        }

        public void instrumentationFinished(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                String pretty = null;
                if (!(this.mRawMode || results == null)) {
                    pretty = results.getString("stream");
                }
                if (pretty != null) {
                    System.out.println(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println("INSTRUMENTATION_RESULT: " + key + "=" + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_CODE: " + resultCode);
                }
                this.mFinished = true;
                notifyAll();
            }
        }

        public boolean waitForFinish() {
            synchronized (this) {
                while (!this.mFinished) {
                    try {
                        if (Am.this.mAm.asBinder().pingBinder()) {
                            wait(1000);
                        } else {
                            return false;
                        }
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return true;
            }
        }
    }

    private class IntentReceiver extends IIntentReceiver.Stub {
        private boolean mFinished;

        private IntentReceiver() {
            this.mFinished = false;
        }

        public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered, boolean sticky, int sendingUser) throws RemoteException {
            String line = "Broadcast completed: result=" + resultCode;
            if (data != null) {
                line = line + ", data=\"" + data + "\"";
            }
            if (extras != null) {
                line = line + ", extras: " + extras;
            }
            System.out.println(line);
            synchronized (this) {
                this.mFinished = true;
                notifyAll();
            }
        }

        public synchronized void waitForFinish() {
            while (!this.mFinished) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    class MyActivityController extends IActivityController.Stub {
        static final int RESULT_ANR_DIALOG = 0;
        static final int RESULT_ANR_KILL = 1;
        static final int RESULT_ANR_WAIT = 1;
        static final int RESULT_CRASH_DIALOG = 0;
        static final int RESULT_CRASH_KILL = 1;
        static final int RESULT_DEFAULT = 0;
        static final int RESULT_EARLY_ANR_CONTINUE = 0;
        static final int RESULT_EARLY_ANR_KILL = 1;
        static final int STATE_ANR = 3;
        static final int STATE_CRASHED = 1;
        static final int STATE_EARLY_ANR = 2;
        static final int STATE_NORMAL = 0;
        final String mGdbPort;
        Process mGdbProcess;
        Thread mGdbThread;
        boolean mGotGdbPrint;
        int mResult;
        int mState;

        MyActivityController(String gdbPort) {
            this.mGdbPort = gdbPort;
        }

        public boolean activityResuming(String pkg) throws RemoteException {
            synchronized (this) {
                System.out.println("** Activity resuming: " + pkg);
            }
            return true;
        }

        public boolean activityStarting(Intent intent, String pkg) throws RemoteException {
            synchronized (this) {
                System.out.println("** Activity starting: " + pkg);
            }
            return true;
        }

        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis, String stackTrace) throws RemoteException {
            boolean z = true;
            synchronized (this) {
                System.out.println("** ERROR: PROCESS CRASHED");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("shortMsg: " + shortMsg);
                System.out.println("longMsg: " + longMsg);
                System.out.println("timeMillis: " + timeMillis);
                System.out.println("stack:");
                System.out.print(stackTrace);
                System.out.println("#");
                if (waitControllerLocked(pid, 1) == 1) {
                    z = false;
                }
            }
            return z;
        }

        public int appEarlyNotResponding(String processName, int pid, String annotation) throws RemoteException {
            int i;
            synchronized (this) {
                System.out.println("** ERROR: EARLY PROCESS NOT RESPONDING");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("annotation: " + annotation);
                if (waitControllerLocked(pid, 2) == 1) {
                    i = -1;
                } else {
                    i = 0;
                }
            }
            return i;
        }

        public int appNotResponding(String processName, int pid, String processStats) throws RemoteException {
            int i = 1;
            synchronized (this) {
                System.out.println("** ERROR: PROCESS NOT RESPONDING");
                System.out.println("processName: " + processName);
                System.out.println("processPid: " + pid);
                System.out.println("processStats:");
                System.out.print(processStats);
                System.out.println("#");
                int result = waitControllerLocked(pid, 3);
                if (result == 1) {
                    i = -1;
                } else if (result == 1) {
                } else {
                    i = 0;
                }
            }
            return i;
        }

        public int systemNotResponding(String msg) throws RemoteException {
            return 0;
        }

        void killGdbLocked() {
            this.mGotGdbPrint = false;
            if (this.mGdbProcess != null) {
                System.out.println("Stopping gdbserver");
                this.mGdbProcess.destroy();
                this.mGdbProcess = null;
            }
            if (this.mGdbThread != null) {
                this.mGdbThread.interrupt();
                this.mGdbThread = null;
            }
        }

        int waitControllerLocked(int pid, int state) {
            if (this.mGdbPort != null) {
                killGdbLocked();
                try {
                    System.out.println("Starting gdbserver on port " + this.mGdbPort);
                    System.out.println("Do the following:");
                    System.out.println("  adb forward tcp:" + this.mGdbPort + " tcp:" + this.mGdbPort);
                    System.out.println("  gdbclient app_process :" + this.mGdbPort);
                    this.mGdbProcess = Runtime.getRuntime().exec(new String[]{"gdbserver", ":" + this.mGdbPort, "--attach", Integer.toString(pid)});
                    final InputStreamReader converter = new InputStreamReader(this.mGdbProcess.getInputStream());
                    this.mGdbThread = new Thread() {
                        /* JADX WARNING: inconsistent code. */
                        /* Code decompiled incorrectly, please refer to instructions dump. */
                        public void run() {
                            /*
                            r7 = this;
                            r2 = new java.io.BufferedReader;
                            r4 = r0;
                            r2.<init>(r4);
                            r0 = 0;
                        L_0x0008:
                            r5 = com.android.internal.telephony.Am.MyActivityController.this;
                            monitor-enter(r5);
                            r4 = com.android.internal.telephony.Am.MyActivityController.this;	 Catch:{ all -> 0x0042 }
                            r4 = r4.mGdbThread;	 Catch:{ all -> 0x0042 }
                            if (r4 != 0) goto L_0x0013;
                        L_0x0011:
                            monitor-exit(r5);	 Catch:{ all -> 0x0042 }
                        L_0x0012:
                            return;
                        L_0x0013:
                            r4 = 2;
                            if (r0 != r4) goto L_0x0020;
                        L_0x0016:
                            r4 = com.android.internal.telephony.Am.MyActivityController.this;	 Catch:{ all -> 0x0042 }
                            r6 = 1;
                            r4.mGotGdbPrint = r6;	 Catch:{ all -> 0x0042 }
                            r4 = com.android.internal.telephony.Am.MyActivityController.this;	 Catch:{ all -> 0x0042 }
                            r4.notifyAll();	 Catch:{ all -> 0x0042 }
                        L_0x0020:
                            monitor-exit(r5);	 Catch:{ all -> 0x0042 }
                            r3 = r2.readLine();	 Catch:{ IOException -> 0x0045 }
                            if (r3 == 0) goto L_0x0012;
                        L_0x0027:
                            r4 = java.lang.System.out;	 Catch:{ IOException -> 0x0045 }
                            r5 = new java.lang.StringBuilder;	 Catch:{ IOException -> 0x0045 }
                            r5.<init>();	 Catch:{ IOException -> 0x0045 }
                            r6 = "GDB: ";
                            r5 = r5.append(r6);	 Catch:{ IOException -> 0x0045 }
                            r5 = r5.append(r3);	 Catch:{ IOException -> 0x0045 }
                            r5 = r5.toString();	 Catch:{ IOException -> 0x0045 }
                            r4.println(r5);	 Catch:{ IOException -> 0x0045 }
                            r0 = r0 + 1;
                            goto L_0x0008;
                        L_0x0042:
                            r4 = move-exception;
                            monitor-exit(r5);	 Catch:{ all -> 0x0042 }
                            throw r4;
                        L_0x0045:
                            r1 = move-exception;
                            goto L_0x0012;
                            */
                            throw new UnsupportedOperationException("Method not decompiled: com.android.internal.telephony.Am.MyActivityController.1.run():void");
                        }
                    };
                    this.mGdbThread.start();
                    try {
                        wait(500);
                    } catch (InterruptedException e) {
                    }
                } catch (IOException e2) {
                    System.err.println("Failure starting gdbserver: " + e2);
                    killGdbLocked();
                }
            }
            this.mState = state;
            System.out.println("");
            printMessageForState();
            while (this.mState != 0) {
                try {
                    wait();
                } catch (InterruptedException e3) {
                }
            }
            killGdbLocked();
            return this.mResult;
        }

        void resumeController(int result) {
            synchronized (this) {
                this.mState = 0;
                this.mResult = result;
                notifyAll();
            }
        }

        void printMessageForState() {
            switch (this.mState) {
                case 0:
                    System.out.println("Monitoring activity manager...  available commands:");
                    break;
                case 1:
                    System.out.println("Waiting after crash...  available commands:");
                    System.out.println("(c)ontinue: show crash dialog");
                    System.out.println("(k)ill: immediately kill app");
                    break;
                case 2:
                    System.out.println("Waiting after early ANR...  available commands:");
                    System.out.println("(c)ontinue: standard ANR processing");
                    System.out.println("(k)ill: immediately kill app");
                    break;
                case 3:
                    System.out.println("Waiting after ANR...  available commands:");
                    System.out.println("(c)ontinue: show ANR dialog");
                    System.out.println("(k)ill: immediately kill app");
                    System.out.println("(w)ait: wait some more");
                    break;
            }
            System.out.println("(q)uit: finish monitoring");
        }

        void run() throws RemoteException {
            printMessageForState();
            Am.this.mAm.setActivityController(this);
            this.mState = 0;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                boolean addNewline = true;
                if (line.length() <= 0) {
                    addNewline = false;
                } else if ("q".equals(line) || "quit".equals(line)) {
                    resumeController(0);
                } else {
                    try {
                        if (this.mState == 1) {
                            if ("c".equals(line) || "continue".equals(line)) {
                                resumeController(0);
                            } else if ("k".equals(line) || "kill".equals(line)) {
                                resumeController(1);
                            } else {
                                System.out.println("Invalid command: " + line);
                            }
                        } else if (this.mState == 3) {
                            if ("c".equals(line) || "continue".equals(line)) {
                                resumeController(0);
                            } else if ("k".equals(line) || "kill".equals(line)) {
                                resumeController(1);
                            } else if ("w".equals(line) || "wait".equals(line)) {
                                resumeController(1);
                            } else {
                                System.out.println("Invalid command: " + line);
                            }
                        } else if (this.mState != 2) {
                            System.out.println("Invalid command: " + line);
                        } else if ("c".equals(line) || "continue".equals(line)) {
                            resumeController(0);
                        } else if ("k".equals(line) || "kill".equals(line)) {
                            resumeController(1);
                        } else {
                            System.out.println("Invalid command: " + line);
                        }
                    } catch (IOException e) {
                        try {
                            e.printStackTrace();
                            return;
                        } finally {
                            Am.this.mAm.setActivityController(null);
                        }
                    }
                }
                synchronized (this) {
                    if (addNewline) {
                        System.out.println("");
                    }
                    printMessageForState();
                }
            }
            resumeController(0);
            Am.this.mAm.setActivityController(null);
        }
    }

    public static void main(String[] args) {
        try {
            new Am().run(args);
        } catch (IllegalArgumentException e) {
            showUsage();
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e2) {
            e2.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void showUsage() {
        System.err.println("usage: am [subcommand] [options]\nusage: am start [-D] [-W] [-P <FILE>] [--start-profiler <FILE>]\n               [--sampling INTERVAL] [-R COUNT] [-S] [--opengl-trace]\n               [--user <USER_ID> | current] <INTENT>\n       am startservice [--user <USER_ID> | current] <INTENT>\n       am stopservice [--user <USER_ID> | current] <INTENT>\n       am force-stop [--user <USER_ID> | all | current] <PACKAGE>\n       am kill [--user <USER_ID> | all | current] <PACKAGE>\n       am kill-all\n       am broadcast [--user <USER_ID> | all | current] <INTENT>\n       am instrument [-r] [-e <NAME> <VALUE>] [-p <FILE>] [-w]\n               [--user <USER_ID> | current]\n               [--no-window-animation]\n               [--abi <ABI>]\n : Launch the instrumented process with the                    selected ABI. This assumes that the process supports the                   selected ABI.               <COMPONENT>\n       am profile start [--user <USER_ID> current] <PROCESS> <FILE>\n       am profile stop [--user <USER_ID> current] [<PROCESS>]\n       am dumpheap [--user <USER_ID> current] [-n] <PROCESS> <FILE>\n       am set-debug-app [-w] [--persistent] <PACKAGE>\n       am clear-debug-app\n       am monitor [--gdb <port>]\n       am hang [--allow-restart]\n       am restart\n       am idle-maintenance\n       am screen-compat [on|off] <PACKAGE>\n       am to-uri [INTENT]\n       am to-intent-uri [INTENT]\n       am switch-user <USER_ID>\n       am stop-user <USER_ID>\n       am stack start <DISPLAY_ID> <INTENT>\n       am stack movetask <TASK_ID> <STACK_ID> [true|false]\n       am stack resize <STACK_ID> <LEFT,TOP,RIGHT,BOTTOM>\n       am stack list\n       am stack info <STACK_ID>\n       am lock-task <TASK_ID>\n       am lock-task stop\n       am get-config\n\nam start: start an Activity.  Options are:\n    -D: enable debugging\n    -W: wait for launch to complete\n    --start-profiler <FILE>: start profiler and send results to <FILE>\n    --sampling INTERVAL: use sample profiling with INTERVAL microseconds\n        between samples (use with --start-profiler)\n    -P <FILE>: like above, but profiling stops when app goes idle\n    -R: repeat the activity launch <COUNT> times.  Prior to each repeat,\n        the top activity will be finished.\n    -S: force stop the target app before starting the activity\n    --opengl-trace: enable tracing of OpenGL functions\n    --user <USER_ID> | current: Specify which user to run as; if not\n        specified then run as the current user.\n\nam startservice: start a Service.  Options are:\n    --user <USER_ID> | current: Specify which user to run as; if not\n        specified then run as the current user.\n\nam stopservice: stop a Service.  Options are:\n    --user <USER_ID> | current: Specify which user to run as; if not\n        specified then run as the current user.\n\nam force-stop: force stop everything associated with <PACKAGE>.\n    --user <USER_ID> | all | current: Specify user to force stop;\n        all users if not specified.\n\nam kill: Kill all processes associated with <PACKAGE>.  Only kills.\n  processes that are safe to kill -- that is, will not impact the user\n  experience.\n    --user <USER_ID> | all | current: Specify user whose processes to kill;\n        all users if not specified.\n\nam kill-all: Kill all background processes.\n\nam broadcast: send a broadcast Intent.  Options are:\n    --user <USER_ID> | all | current: Specify which user to send to; if not\n        specified then send to all users.\n    --receiver-permission <PERMISSION>: Require receiver to hold permission.\n\nam instrument: start an Instrumentation.  Typically this target <COMPONENT>\n  is the form <TEST_PACKAGE>/<RUNNER_CLASS>.  Options are:\n    -r: print raw results (otherwise decode REPORT_KEY_STREAMRESULT).  Use with\n        [-e perf true] to generate raw output for performance measurements.\n    -e <NAME> <VALUE>: set argument <NAME> to <VALUE>.  For test runners a\n        common form is [-e <testrunner_flag> <value>[,<value>...]].\n    -p <FILE>: write profiling data to <FILE>\n    -w: wait for instrumentation to finish before returning.  Required for\n        test runners.\n    --user <USER_ID> | current: Specify user instrumentation runs in;\n        current user if not specified.\n    --no-window-animation: turn off window animations while running.\n\nam profile: start and stop profiler on a process.  The given <PROCESS> argument\n  may be either a process name or pid.  Options are:\n    --user <USER_ID> | current: When supplying a process name,\n        specify user of process to profile; uses current user if not specified.\n\nam dumpheap: dump the heap of a process.  The given <PROCESS> argument may\n  be either a process name or pid.  Options are:\n    -n: dump native heap instead of managed heap\n    --user <USER_ID> | current: When supplying a process name,\n        specify user of process to dump; uses current user if not specified.\n\nam set-debug-app: set application <PACKAGE> to debug.  Options are:\n    -w: wait for debugger when application starts\n    --persistent: retain this value\n\nam clear-debug-app: clear the previously set-debug-app.\n\nam bug-report: request bug report generation; will launch UI\n    when done to select where it should be delivered.\n\nam monitor: start monitoring for crashes or ANRs.\n    --gdb: start gdbserv on the given port at crash/ANR\n\nam hang: hang the system.\n    --allow-restart: allow watchdog to perform normal system restart\n\nam restart: restart the user-space system.\n\nam idle-maintenance: perform idle maintenance now.\n\nam screen-compat: control screen compatibility mode of <PACKAGE>.\n\nam to-uri: print the given Intent specification as a URI.\n\nam to-intent-uri: print the given Intent specification as an intent: URI.\n\nam switch-user: switch to put USER_ID in the foreground, starting\n  execution of that user if it is currently stopped.\n\nam stop-user: stop execution of USER_ID, not allowing it to run any\n  code until a later explicit switch to it.\n\nam stack start: start a new activity on <DISPLAY_ID> using <INTENT>.\n\nam stack movetask: move <TASK_ID> from its current stack to the top (true) or   bottom (false) of <STACK_ID>.\n\nam stack resize: change <STACK_ID> size and position to <LEFT,TOP,RIGHT,BOTTOM>.\n\nam stack list: list all of the activity stacks and their sizes.\n\nam stack info: display the information about activity stack <STACK_ID>.\n\nam lock-task: bring <TASK_ID> to the front and don't allow other tasks to run\n\nam get-config: retrieve the configuration and any recent configurations\n  of the device\n\n<INTENT> specifications include these flags and arguments:\n    [-a <ACTION>] [-d <DATA_URI>] [-t <MIME_TYPE>]\n    [-c <CATEGORY> [-c <CATEGORY>] ...]\n    [-e|--es <EXTRA_KEY> <EXTRA_STRING_VALUE> ...]\n    [--esn <EXTRA_KEY> ...]\n    [--ez <EXTRA_KEY> <EXTRA_BOOLEAN_VALUE> ...]\n    [--ei <EXTRA_KEY> <EXTRA_INT_VALUE> ...]\n    [--el <EXTRA_KEY> <EXTRA_LONG_VALUE> ...]\n    [--ef <EXTRA_KEY> <EXTRA_FLOAT_VALUE> ...]\n    [--eu <EXTRA_KEY> <EXTRA_URI_VALUE> ...]\n    [--ecn <EXTRA_KEY> <EXTRA_COMPONENT_NAME_VALUE>]\n    [--eia <EXTRA_KEY> <EXTRA_INT_VALUE>[,<EXTRA_INT_VALUE...]]\n    [--ela <EXTRA_KEY> <EXTRA_LONG_VALUE>[,<EXTRA_LONG_VALUE...]]\n    [--efa <EXTRA_KEY> <EXTRA_FLOAT_VALUE>[,<EXTRA_FLOAT_VALUE...]]\n    [--esa <EXTRA_KEY> <EXTRA_STRING_VALUE>[,<EXTRA_STRING_VALUE...]]\n        (to embed a comma into a string escape it using \"\\,\")\n    [-n <COMPONENT>] [-f <FLAGS>]\n    [--grant-read-uri-permission] [--grant-write-uri-permission]\n    [--grant-persistable-uri-permission] [--grant-prefix-uri-permission]\n    [--debug-log-resolution] [--exclude-stopped-packages]\n    [--include-stopped-packages]\n    [--activity-brought-to-front] [--activity-clear-top]\n    [--activity-clear-when-task-reset] [--activity-exclude-from-recents]\n    [--activity-launched-from-history] [--activity-multiple-task]\n    [--activity-no-animation] [--activity-no-history]\n    [--activity-no-user-action] [--activity-previous-is-top]\n    [--activity-reorder-to-front] [--activity-reset-task-if-needed]\n    [--activity-single-top] [--activity-clear-task]\n    [--activity-task-on-home]\n    [--receiver-registered-only] [--receiver-replace-pending]\n    [--selector]\n    [<URI> | <PACKAGE> | <COMPONENT>]\n");
    }

    private void run(String[] args) throws Exception {
        if (args.length < 1) {
            showUsage();
            return;
        }
        this.mAm = ActivityManagerNative.getDefault();
        if (this.mAm == null) {
            System.err.println(NO_SYSTEM_ERROR_CODE);
            throw new AndroidException("Can't connect to activity manager; is the system running?");
        }
        this.mArgs = args;
        String op = args[0];
        this.mNextArg = 1;
        if (op.equals(BaseMmsColumns.START)) {
            runStart();
        } else if (op.equals("startservice")) {
            runStartService();
        } else if (op.equals("stopservice")) {
            runStopService();
        } else if (op.equals("force-stop")) {
            runForceStop();
        } else if (op.equals("kill")) {
            runKill();
        } else if (op.equals("kill-all")) {
            runKillAll();
        } else if (op.equals("instrument")) {
            runInstrument();
        } else if (op.equals("broadcast")) {
            sendBroadcast();
        } else if (op.equals("profile")) {
            runProfile();
        } else if (op.equals("dumpheap")) {
            runDumpHeap();
        } else if (op.equals("set-debug-app")) {
            runSetDebugApp();
        } else if (op.equals("clear-debug-app")) {
            runClearDebugApp();
        } else if (op.equals("bug-report")) {
            runBugReport();
        } else if (op.equals("monitor")) {
            runMonitor();
        } else if (op.equals("hang")) {
            runHang();
        } else if (op.equals("restart")) {
            runRestart();
        } else if (op.equals("idle-maintenance")) {
            runIdleMaintenance();
        } else if (op.equals("screen-compat")) {
            runScreenCompat();
        } else if (op.equals("to-uri")) {
            runToUri(false);
        } else if (op.equals("to-intent-uri")) {
            runToUri(true);
        } else if (op.equals("switch-user")) {
            runSwitchUser();
        } else if (op.equals("stop-user")) {
            runStopUser();
        } else {
            throw new IllegalArgumentException("Unknown command: " + op);
        }
    }

    public static void main(Context context, String[] args) {
        try {
            new Am().runExt(context, args);
        } catch (IllegalArgumentException e) {
            showUsage();
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println(e2.toString());
        }
    }

    private void runExt(Context context, String[] args) throws Exception {
        if (args.length < 1) {
            showUsage();
            return;
        }
        this.mArgs = args;
        String op = args[0];
        this.mNextArg = 1;
        if (op.equals("broadcast")) {
            Intent intent = makeIntent(-1);
            if (intent != null) {
                System.out.println("Broadcasting: " + intent);
                context.sendBroadcast(intent);
                return;
            }
            return;
        }
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            showUsage();
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e2) {
            System.err.println(e2.toString());
        }
    }

    int parseUserArg(String arg) {
        if ("all".equals(arg)) {
            return -1;
        }
        if ("current".equals(arg) || "cur".equals(arg)) {
            return -2;
        }
        return Integer.parseInt(arg);
    }

    private Intent makeIntent(int defUser) throws URISyntaxException {
        Intent intent = new Intent();
        Intent baseIntent = intent;
        boolean hasIntentInfo = false;
        this.mStartFlags = 0;
        this.mWaitOption = false;
        this.mStopOption = false;
        this.mRepeat = 0;
        this.mProfileFile = null;
        this.mSamplingInterval = 0;
        this.mAutoStop = false;
        this.mUserId = defUser;
        Uri data = null;
        String type = null;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                break;
            } else if (opt.equals("-a")) {
                intent.setAction(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-d")) {
                data = Uri.parse(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-t")) {
                type = nextArgRequired();
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-c")) {
                intent.addCategory(nextArgRequired());
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-e") || opt.equals("--es")) {
                intent.putExtra(nextArgRequired(), nextArgRequired());
            } else if (opt.equals("--esn")) {
                intent.putExtra(nextArgRequired(), (String) null);
            } else if (opt.equals("--ei")) {
                intent.putExtra(nextArgRequired(), Integer.valueOf(nextArgRequired()));
            } else if (opt.equals("--eu")) {
                intent.putExtra(nextArgRequired(), Uri.parse(nextArgRequired()));
            } else if (opt.equals("--ecn")) {
                key = nextArgRequired();
                String value = nextArgRequired();
                cn = ComponentName.unflattenFromString(value);
                if (cn == null) {
                    throw new IllegalArgumentException("Bad component name: " + value);
                }
                intent.putExtra(key, cn);
            } else if (opt.equals("--eia")) {
                key = nextArgRequired();
                strings = nextArgRequired().split(",");
                int[] list = new int[strings.length];
                for (i = 0; i < strings.length; i++) {
                    list[i] = Integer.valueOf(strings[i]).intValue();
                }
                intent.putExtra(key, list);
            } else if (opt.equals("--el")) {
                intent.putExtra(nextArgRequired(), Long.valueOf(nextArgRequired()));
            } else if (opt.equals("--ela")) {
                key = nextArgRequired();
                strings = nextArgRequired().split(",");
                long[] list2 = new long[strings.length];
                for (i = 0; i < strings.length; i++) {
                    list2[i] = Long.valueOf(strings[i]).longValue();
                }
                intent.putExtra(key, list2);
                hasIntentInfo = true;
            } else if (opt.equals("--ef")) {
                intent.putExtra(nextArgRequired(), Float.valueOf(nextArgRequired()));
                hasIntentInfo = true;
            } else if (opt.equals("--efa")) {
                key = nextArgRequired();
                strings = nextArgRequired().split(",");
                float[] list3 = new float[strings.length];
                for (i = 0; i < strings.length; i++) {
                    list3[i] = Float.valueOf(strings[i]).floatValue();
                }
                intent.putExtra(key, list3);
                hasIntentInfo = true;
            } else if (opt.equals("--esa")) {
                intent.putExtra(nextArgRequired(), nextArgRequired().split("(?<!\\\\),"));
                hasIntentInfo = true;
            } else if (opt.equals("--ez")) {
                intent.putExtra(nextArgRequired(), Boolean.valueOf(nextArgRequired()));
            } else if (opt.equals("-n")) {
                String str = nextArgRequired();
                cn = ComponentName.unflattenFromString(str);
                if (cn == null) {
                    throw new IllegalArgumentException("Bad component name: " + str);
                }
                intent.setComponent(cn);
                if (intent == baseIntent) {
                    hasIntentInfo = true;
                }
            } else if (opt.equals("-f")) {
                intent.setFlags(Integer.decode(nextArgRequired()).intValue());
            } else if (opt.equals("--grant-read-uri-permission")) {
                intent.addFlags(1);
            } else if (opt.equals("--grant-write-uri-permission")) {
                intent.addFlags(2);
            } else if (opt.equals("--grant-persistable-uri-permission")) {
                intent.addFlags(64);
            } else if (opt.equals("--grant-prefix-uri-permission")) {
                intent.addFlags(128);
            } else if (opt.equals("--exclude-stopped-packages")) {
                intent.addFlags(16);
            } else if (opt.equals("--include-stopped-packages")) {
                intent.addFlags(32);
            } else if (opt.equals("--debug-log-resolution")) {
                intent.addFlags(8);
            } else if (opt.equals("--activity-brought-to-front")) {
                intent.addFlags(4194304);
            } else if (opt.equals("--activity-clear-top")) {
                intent.addFlags(67108864);
            } else if (opt.equals("--activity-clear-when-task-reset")) {
                intent.addFlags(524288);
            } else if (opt.equals("--activity-exclude-from-recents")) {
                intent.addFlags(8388608);
            } else if (opt.equals("--activity-launched-from-history")) {
                intent.addFlags(1048576);
            } else if (opt.equals("--activity-multiple-task")) {
                intent.addFlags(134217728);
            } else if (opt.equals("--activity-no-animation")) {
                intent.addFlags(65536);
            } else if (opt.equals("--activity-no-history")) {
                intent.addFlags(1073741824);
            } else if (opt.equals("--activity-no-user-action")) {
                intent.addFlags(SmsEnvelope.TELESERVICE_MWI);
            } else if (opt.equals("--activity-previous-is-top")) {
                intent.addFlags(16777216);
            } else if (opt.equals("--activity-reorder-to-front")) {
                intent.addFlags(131072);
            } else if (opt.equals("--activity-reset-task-if-needed")) {
                intent.addFlags(2097152);
            } else if (opt.equals("--activity-single-top")) {
                intent.addFlags(536870912);
            } else if (opt.equals("--activity-clear-task")) {
                intent.addFlags(WapPushManagerParams.FURTHER_PROCESSING);
            } else if (opt.equals("--activity-task-on-home")) {
                intent.addFlags(16384);
            } else if (opt.equals("--receiver-registered-only")) {
                intent.addFlags(1073741824);
            } else if (opt.equals("--receiver-replace-pending")) {
                intent.addFlags(536870912);
            } else if (opt.equals("--selector")) {
                intent.setDataAndType(data, type);
                intent = new Intent();
            } else if (opt.equals("-D")) {
                this.mStartFlags |= 2;
            } else if (opt.equals("-W")) {
                this.mWaitOption = true;
            } else if (opt.equals("-P")) {
                this.mProfileFile = nextArgRequired();
                this.mAutoStop = true;
            } else if (opt.equals("--start-profiler")) {
                this.mProfileFile = nextArgRequired();
                this.mAutoStop = false;
            } else if (opt.equals("--sampling")) {
                this.mSamplingInterval = Integer.parseInt(nextArgRequired());
            } else if (opt.equals("-R")) {
                this.mRepeat = Integer.parseInt(nextArgRequired());
            } else if (opt.equals("-S")) {
                this.mStopOption = true;
            } else if (opt.equals("--opengl-trace")) {
                this.mStartFlags |= 4;
            } else if (opt.equals("--user")) {
                this.mUserId = parseUserArg(nextArgRequired());
            } else if (opt.equals("--receiver-permission")) {
                this.mReceiverPermission = nextArgRequired();
            } else {
                if (opt.equals("-p")) {
                    System.err.println("Error: Unknown option: " + opt);
                } else {
                    System.err.println("Error: Unknown option: " + opt);
                }
                return null;
            }
        }
        intent.setDataAndType(data, type);
        boolean hasSelector = intent != baseIntent;
        if (hasSelector) {
            baseIntent.setSelector(intent);
            intent = baseIntent;
        }
        String arg = nextArg();
        baseIntent = null;
        if (arg == null) {
            if (hasSelector) {
                baseIntent = new Intent("android.intent.action.MAIN");
                baseIntent.addCategory("android.intent.category.LAUNCHER");
            }
        } else if (arg.indexOf(58) >= 0) {
            baseIntent = Intent.parseUri(arg, 1);
        } else if (arg.indexOf(47) >= 0) {
            baseIntent = new Intent("android.intent.action.MAIN");
            baseIntent.addCategory("android.intent.category.LAUNCHER");
            baseIntent.setComponent(ComponentName.unflattenFromString(arg));
        } else {
            baseIntent = new Intent("android.intent.action.MAIN");
            baseIntent.addCategory("android.intent.category.LAUNCHER");
            baseIntent.setPackage(arg);
        }
        if (baseIntent != null) {
            Bundle extras = intent.getExtras();
            intent.replaceExtras((Bundle) null);
            Bundle uriExtras = baseIntent.getExtras();
            baseIntent.replaceExtras((Bundle) null);
            if (!(intent.getAction() == null || baseIntent.getCategories() == null)) {
                Iterator i$ = new HashSet(baseIntent.getCategories()).iterator();
                while (i$.hasNext()) {
                    baseIntent.removeCategory((String) i$.next());
                }
            }
            intent.fillIn(baseIntent, 72);
            if (extras == null) {
                extras = uriExtras;
            } else if (uriExtras != null) {
                uriExtras.putAll(extras);
                extras = uriExtras;
            }
            intent.replaceExtras(extras);
            hasIntentInfo = true;
        }
        if (hasIntentInfo) {
            return intent;
        }
        throw new IllegalArgumentException("No intent supplied");
    }

    private void runStartService() throws Exception {
        Intent intent = makeIntent(-2);
        if (this.mUserId == -1) {
            System.err.println("Error: Can't start activity with user 'all'");
            return;
        }
        System.out.println("Starting service: " + intent);
        ComponentName cn = this.mAm.startService(null, intent, intent.getType(), this.mUserId);
        if (cn == null) {
            System.err.println("Error: Not found; no service started.");
        } else if (cn.getPackageName().equals("!")) {
            System.err.println("Error: Requires permission " + cn.getClassName());
        } else if (cn.getPackageName().equals("!!")) {
            System.err.println("Error: " + cn.getClassName());
        }
    }

    private void runStopService() throws Exception {
        Intent intent = makeIntent(-2);
        if (this.mUserId == -1) {
            System.err.println("Error: Can't stop activity with user 'all'");
            return;
        }
        System.out.println("Stopping service: " + intent);
        int result = this.mAm.stopService(null, intent, intent.getType(), this.mUserId);
        if (result == 0) {
            System.err.println("Service not stopped: was not running.");
        } else if (result == 1) {
            System.err.println("Service stopped");
        } else if (result == -1) {
            System.err.println("Error stopping service");
        }
    }

    private void runStart() throws Exception {
        Intent intent = makeIntent(-2);
        if (this.mUserId == -1) {
            System.err.println("Error: Can't start service with user 'all'");
            return;
        }
        String mimeType = intent.getType();
        if (mimeType == null && intent.getData() != null && "content".equals(intent.getData().getScheme())) {
            mimeType = this.mAm.getProviderMimeType(intent.getData(), this.mUserId);
        }
        do {
            int res;
            if (this.mStopOption) {
                String packageName;
                if (intent.getComponent() != null) {
                    packageName = intent.getComponent().getPackageName();
                } else {
                    IPackageManager pm = IPackageManager.Stub.asInterface(ServiceManager.getService(Intents.EXTRA_PACKAGE_NAME));
                    if (pm == null) {
                        System.err.println("Error: Package manager not running; aborting");
                        return;
                    }
                    List<ResolveInfo> activities = pm.queryIntentActivities(intent, mimeType, 0, this.mUserId);
                    if (activities == null || activities.size() <= 0) {
                        System.err.println("Error: Intent does not match any activities: " + intent);
                        return;
                    } else if (activities.size() > 1) {
                        System.err.println("Error: Intent matches multiple activities; can't stop: " + intent);
                        return;
                    } else {
                        packageName = ((ResolveInfo) activities.get(0)).activityInfo.packageName;
                    }
                }
                System.out.println("Stopping: " + packageName);
                this.mAm.forceStopPackage(packageName, this.mUserId);
                Thread.sleep(250);
            }
            System.out.println("Starting: " + intent);
            intent.addFlags(268435456);
            ProfilerInfo profilerInfo = null;
            if (this.mProfileFile != null) {
                try {
                    profilerInfo = new ProfilerInfo(this.mProfileFile, ParcelFileDescriptor.open(new File(this.mProfileFile), 1006632960), this.mSamplingInterval, this.mAutoStop);
                } catch (FileNotFoundException e) {
                    System.err.println("Error: Unable to open file: " + this.mProfileFile);
                    return;
                }
            }
            WaitResult result = null;
            long startTime = SystemClock.uptimeMillis();
            if (this.mWaitOption) {
                result = this.mAm.startActivityAndWait(null, getClass().getPackage().getName(), intent, mimeType, null, null, 0, this.mStartFlags, profilerInfo, null, this.mUserId);
                res = result.result;
            } else {
                res = this.mAm.startActivityAsUser(null, getClass().getPackage().getName(), intent, mimeType, null, null, 0, this.mStartFlags, profilerInfo, null, this.mUserId);
            }
            long endTime = SystemClock.uptimeMillis();
            PrintStream out = this.mWaitOption ? System.out : System.err;
            boolean launched = false;
            switch (res) {
                case -4:
                    out.println("Error: Activity not started, you do not have permission to access it.");
                    break;
                case SubInfoRecordUpdater.SIM_REPOSITION /*-3*/:
                    out.println("Error: Activity not started, you requested to both forward and receive its result");
                    break;
                case SubInfoRecordUpdater.SIM_NEW /*-2*/:
                    out.println(NO_CLASS_ERROR_CODE);
                    out.println("Error: Activity class " + intent.getComponent().toShortString() + " does not exist.");
                    break;
                case -1:
                    out.println("Error: Activity not started, unable to resolve " + intent.toString());
                    break;
                case 0:
                    launched = true;
                    break;
                case 1:
                    launched = true;
                    out.println("Warning: Activity not started because intent should be handled by the caller");
                    break;
                case 2:
                    launched = true;
                    out.println("Warning: Activity not started, its current task has been brought to the front");
                    break;
                case 3:
                    launched = true;
                    out.println("Warning: Activity not started, intent has been delivered to currently running top-most instance.");
                    break;
                case 4:
                    launched = true;
                    out.println("Warning: Activity not started because the  current activity is being kept for the user.");
                    break;
                default:
                    out.println("Error: Activity not started, unknown error code " + res);
                    break;
            }
            if (this.mWaitOption && launched) {
                if (result == null) {
                    result = new WaitResult();
                    result.who = intent.getComponent();
                }
                System.out.println("Status: " + (result.timeout ? "timeout" : "ok"));
                if (result.who != null) {
                    System.out.println("Activity: " + result.who.flattenToShortString());
                }
                if (result.thisTime >= 0) {
                    System.out.println("ThisTime: " + result.thisTime);
                }
                if (result.totalTime >= 0) {
                    System.out.println("TotalTime: " + result.totalTime);
                }
                System.out.println("WaitTime: " + (endTime - startTime));
                System.out.println("Complete");
            }
            this.mRepeat--;
            if (this.mRepeat > 1) {
                this.mAm.unhandledBack();
            }
        } while (this.mRepeat > 1);
    }

    private void runForceStop() throws Exception {
        int userId = -1;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                this.mAm.forceStopPackage(nextArgRequired(), userId);
                return;
            } else if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
    }

    private void runKill() throws Exception {
        int userId = -1;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                this.mAm.killBackgroundProcesses(nextArgRequired(), userId);
                return;
            } else if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
    }

    private void runKillAll() throws Exception {
        this.mAm.killAllBackgroundProcesses();
    }

    private void sendBroadcast() throws Exception {
        Intent intent = makeIntent(-2);
        IntentReceiver receiver = new IntentReceiver();
        System.out.println("Broadcasting: " + intent);
        if (intent != null) {
            this.mAm.broadcastIntent(null, intent, null, receiver, 0, null, null, this.mReceiverPermission, -1, true, false, this.mUserId);
        }
    }

    private void runInstrument() throws Exception {
        String profileFile = null;
        boolean wait = false;
        boolean rawMode = false;
        boolean no_window_animation = false;
        int userId = -2;
        Bundle args = new Bundle();
        IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                break;
            } else if (opt.equals("-p")) {
                profileFile = nextArgRequired();
            } else if (opt.equals("-w")) {
                wait = true;
            } else if (opt.equals("-r")) {
                rawMode = true;
            } else if (opt.equals("-e")) {
                args.putString(nextArgRequired(), nextArgRequired());
            } else if (opt.equals("--no_window_animation") || opt.equals("--no-window-animation")) {
                no_window_animation = true;
            } else if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
        if (userId == -1) {
            System.err.println("Error: Can't start instrumentation with user 'all'");
            return;
        }
        String cnArg = nextArgRequired();
        ComponentName cn = ComponentName.unflattenFromString(cnArg);
        if (cn == null) {
            throw new IllegalArgumentException("Bad component name: " + cnArg);
        }
        InstrumentationWatcher watcher = null;
        if (wait) {
            Am am = this;
            watcher = new InstrumentationWatcher();
            watcher.setRawOutput(rawMode);
        }
        float[] oldAnims = null;
        if (no_window_animation) {
            oldAnims = wm.getAnimationScales();
            wm.setAnimationScale(0, 0.0f);
            wm.setAnimationScale(1, 0.0f);
        }
        if (this.mAm.startInstrumentation(cn, profileFile, 0, args, watcher, null, userId, null)) {
            if (!(watcher == null || watcher.waitForFinish())) {
                System.out.println("INSTRUMENTATION_ABORTED: System has crashed.");
            }
            if (oldAnims != null) {
                wm.setAnimationScales(oldAnims);
                return;
            }
            return;
        }
        throw new AndroidException("INSTRUMENTATION_FAILED: " + cn.flattenToString());
    }

    static void removeWallOption() {
        String props = SystemProperties.get("dalvik.vm.extra-opts");
        if (props != null && props.contains("-Xprofile:wallclock")) {
            SystemProperties.set("dalvik.vm.extra-opts", props.replace("-Xprofile:wallclock", "").trim());
        }
    }

    private void runProfile() throws Exception {
        String process;
        boolean start = false;
        boolean wall = false;
        int userId = -2;
        String cmd = nextArgRequired();
        String opt;
        if (BaseMmsColumns.START.equals(cmd)) {
            start = true;
            while (true) {
                opt = nextOption();
                if (opt == null) {
                    break;
                } else if (opt.equals("--user")) {
                    userId = parseUserArg(nextArgRequired());
                } else if (opt.equals("--wall")) {
                    wall = true;
                } else {
                    System.err.println("Error: Unknown option: " + opt);
                    return;
                }
            }
            process = nextArgRequired();
        } else if ("stop".equals(cmd)) {
            while (true) {
                opt = nextOption();
                if (opt == null) {
                    break;
                } else if (opt.equals("--user")) {
                    userId = parseUserArg(nextArgRequired());
                } else {
                    System.err.println("Error: Unknown option: " + opt);
                    return;
                }
            }
            process = nextArg();
        } else {
            process = cmd;
            cmd = nextArgRequired();
            if (BaseMmsColumns.START.equals(cmd)) {
                start = true;
            } else if (!"stop".equals(cmd)) {
                throw new IllegalArgumentException("Profile command " + process + " not valid");
            }
        }
        if (userId == -1) {
            System.err.println("Error: Can't profile with user 'all'");
            return;
        }
        ProfilerInfo profilerInfo = null;
        if (start) {
            String profileFile = nextArgRequired();
            try {
                profilerInfo = new ProfilerInfo(profileFile, ParcelFileDescriptor.open(new File(profileFile), 1006632960), 0, false);
            } catch (FileNotFoundException e) {
                System.err.println("Error: Unable to open file: " + profileFile);
                return;
            }
        }
        if (wall) {
            try {
                String props = SystemProperties.get("dalvik.vm.extra-opts");
                if (props == null || !props.contains("-Xprofile:wallclock")) {
                    props + " -Xprofile:wallclock";
                }
            } catch (Throwable th) {
                if (wall) {
                }
            }
        } else if (start) {
        }
        if (!this.mAm.profileControl(process, userId, start, profilerInfo, 0)) {
            throw new AndroidException("PROFILE FAILED on process " + process);
        } else if (!wall) {
        }
    }

    private void runDumpHeap() throws Exception {
        boolean managed = true;
        int userId = -2;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                break;
            } else if (opt.equals("--user")) {
                userId = parseUserArg(nextArgRequired());
                if (userId == -1) {
                    System.err.println("Error: Can't dump heap with user 'all'");
                    return;
                }
            } else if (opt.equals("-n")) {
                managed = false;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
        String process = nextArgRequired();
        String heapFile = nextArgRequired();
        try {
            File file = new File(heapFile);
            file.delete();
            if (!this.mAm.dumpHeap(process, userId, managed, heapFile, ParcelFileDescriptor.open(file, 1006632960))) {
                throw new AndroidException("HEAP DUMP FAILED on process " + process);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error: Unable to open file: " + heapFile);
        }
    }

    private void runSetDebugApp() throws Exception {
        boolean wait = false;
        boolean persistent = false;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                this.mAm.setDebugApp(nextArgRequired(), wait, persistent);
                return;
            } else if (opt.equals("-w")) {
                wait = true;
            } else if (opt.equals("--persistent")) {
                persistent = true;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
    }

    private void runClearDebugApp() throws Exception {
        this.mAm.setDebugApp(null, false, true);
    }

    private void runBugReport() throws Exception {
        this.mAm.requestBugReport();
        System.out.println("Your lovely bug report is being created; please be patient.");
    }

    private void runSwitchUser() throws Exception {
        this.mAm.switchUser(Integer.parseInt(nextArgRequired()));
    }

    private void runStopUser() throws Exception {
        String user = nextArgRequired();
        int res = this.mAm.stopUser(Integer.parseInt(user), null);
        if (res != 0) {
            String txt = "";
            switch (res) {
                case SubInfoRecordUpdater.SIM_NEW /*-2*/:
                    txt = " (Can't stop current user)";
                    break;
                case -1:
                    txt = " (Unknown user " + user + ")";
                    break;
            }
            System.err.println("Switch failed: " + res + txt);
        }
    }

    private void runMonitor() throws Exception {
        String gdbPort = null;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                new MyActivityController(gdbPort).run();
                return;
            } else if (opt.equals("--gdb")) {
                gdbPort = nextArgRequired();
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
    }

    private void runHang() throws Exception {
        boolean allowRestart = false;
        while (true) {
            String opt = nextOption();
            if (opt == null) {
                System.out.println("Hanging the system...");
                this.mAm.hang(new Binder(), allowRestart);
                return;
            } else if (opt.equals("--allow-restart")) {
                allowRestart = true;
            } else {
                System.err.println("Error: Unknown option: " + opt);
                return;
            }
        }
    }

    private void runRestart() throws Exception {
        String opt = nextOption();
        if (opt != null) {
            System.err.println("Error: Unknown option: " + opt);
            return;
        }
        System.out.println("Restart the system...");
        this.mAm.restart();
    }

    private void runIdleMaintenance() throws Exception {
        String opt = nextOption();
        if (opt != null) {
            System.err.println("Error: Unknown option: " + opt);
            return;
        }
        System.out.println("Performing idle maintenance...");
        this.mAm.broadcastIntent(null, new Intent("com.android.server.task.controllers.IdleController.ACTION_TRIGGER_IDLE"), null, null, 0, null, null, null, -1, true, false, -1);
    }

    private void runScreenCompat() throws Exception {
        boolean enabled;
        String mode = nextArgRequired();
        if ("on".equals(mode)) {
            enabled = true;
        } else if ("off".equals(mode)) {
            enabled = false;
        } else {
            System.err.println("Error: enabled mode must be 'on' or 'off' at " + mode);
            return;
        }
        String packageName = nextArgRequired();
        do {
            try {
                this.mAm.setPackageScreenCompatMode(packageName, enabled ? 1 : 0);
            } catch (RemoteException e) {
            }
            packageName = nextArg();
        } while (packageName != null);
    }

    private void runToUri(boolean intentScheme) throws Exception {
        System.out.println(makeIntent(-2).toUri(intentScheme ? 1 : 0));
    }

    private String nextOption() {
        if (this.mCurArgData != null) {
            throw new IllegalArgumentException("No argument expected after \"" + this.mArgs[this.mNextArg - 1] + "\"");
        } else if (this.mNextArg >= this.mArgs.length) {
            return null;
        } else {
            String arg = this.mArgs[this.mNextArg];
            if (!arg.startsWith("-")) {
                return null;
            }
            this.mNextArg++;
            if (arg.equals("--")) {
                return null;
            }
            if (arg.length() <= 1 || arg.charAt(1) == '-') {
                this.mCurArgData = null;
                return arg;
            } else if (arg.length() > 2) {
                this.mCurArgData = arg.substring(2);
                return arg.substring(0, 2);
            } else {
                this.mCurArgData = null;
                return arg;
            }
        }
    }

    private String nextArg() {
        if (this.mCurArgData != null) {
            String arg = this.mCurArgData;
            this.mCurArgData = null;
            return arg;
        } else if (this.mNextArg >= this.mArgs.length) {
            return null;
        } else {
            String[] strArr = this.mArgs;
            int i = this.mNextArg;
            this.mNextArg = i + 1;
            return strArr[i];
        }
    }

    private String nextArgRequired() {
        String arg = nextArg();
        if (arg != null) {
            return arg;
        }
        throw new IllegalArgumentException("Argument expected after \"" + this.mArgs[this.mNextArg - 1] + "\"");
    }
}
