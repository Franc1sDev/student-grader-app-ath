package com.example.myfirstappath;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 Android does not allow internet calls on the main (screen) thread, because the screen would freeze.
 BackgroundTask.run(...) does the work on a background thread, then gives the result back on the main
 thread so the page can update its views. If the page has closed in the meantime, the result is ignored.
*/
public final class BackgroundTask {

    private BackgroundTask() { }

    public interface Work<T> { T run() throws Exception; }

    /** error is null if the work succeeded; result is null if it failed. */
    public interface Done<T> { void onDone(T result, Exception error); }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());

    public static <T> void run(Activity page, Work<T> work, Done<T> done) {
        EXECUTOR.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = work.run();
            } catch (Exception e) {
                error = e;
            }
            final T finalResult = result;
            final Exception finalError = error;
            MAIN_THREAD.post(() -> {
                if (page.isFinishing() || page.isDestroyed()) return;
                done.onDone(finalResult, finalError);
            });
        });
    }
}
