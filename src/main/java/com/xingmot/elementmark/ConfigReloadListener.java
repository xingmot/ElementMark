package com.xingmot.elementmark;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 把配置文件挂进客户端资源重载链。
 *
 * 触发时机：游戏启动、F3+T、资源包切换。
 * 重载后必须调用 {@link BadgeResolver#invalidate()}，否则按物品缓存的旧缩写不会更新。
 */
public final class ConfigReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.runAsync(() -> {
            ConfigLoader.reload();
            BadgeResolver.invalidate();
        }, backgroundExecutor).thenCompose(barrier::wait);
    }
}
