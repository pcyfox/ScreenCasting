package com.taike.lib_rtp_player.rtsp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class PlayerThreadPool {
    private static final String TAG = "ThreadPool";
    private ExecutorService executorService;

    private PlayerThreadPool() {
        if (executorService != null) {
            return;
        }
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "FUCK RTSP ThreadPool");
            }
        });
    }

    private final static PlayerThreadPool instance = new PlayerThreadPool();

    public static PlayerThreadPool getInstance() {
        return instance;
    }

    public void submit(Runnable runnable) {
        if (!executorService.isShutdown()) {
            executorService.submit(runnable);
        }
    }

    public void shutDown() {
        executorService.shutdown();
    }

    public boolean isShtDown() {
        return executorService.isShutdown();
    }

    public static int getNumAvailableCores() {
        return Runtime.getRuntime().availableProcessors();
    }

}
