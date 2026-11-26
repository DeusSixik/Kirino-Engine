package com.cleanroommc.kirino.debug.ecs;

import com.cleanroommc.kirino.KirinoCommonCore;
import com.cleanroommc.kirino.ecs.api.CleanECSRuntime;
import com.cleanroommc.kirino.ecs.api.component.scan.event.ComponentScanningEvent;
import com.cleanroommc.kirino.ecs.api.job.event.JobRegistrationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class EcsDebugCommand extends CommandBase {
    private static final int BASELINE_FRAMES = 60;
    private static final int MAX_SAMPLES = 8192;
    private static final int DEFAULT_ROWS = 25_000;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_WORKERS = 16;
    private static final String USAGE = "/kirinoecs <all|bulk|fragmented|migration|concurrent|lifecycle|status> [entities] [workers]";
    private static final String[] ACTIONS = {"all", "bulk", "fragmented", "migration", "concurrent", "lifecycle", "status"};

    private final Supplier<CleanECSRuntime> runtime;
    private Run active;

    public EcsDebugCommand(Supplier<CleanECSRuntime> runtime) {
        this.runtime = runtime;
    }

    public static void registerComponents(ComponentScanningEvent event) {
        event.register(EcsDebugSuite.class.getPackageName());
    }

    public static void registerJobs(JobRegistrationEvent event) {
        event.register(EcsDebugSuite.VerifyRows.class);
    }

    @Override
    public String getName() {
        return "kirinoecs";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, ACTIONS) : List.of();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equals("status")) {
            chat(sender, active == null ? "No ECS test is running" : active.status);
            return;
        }
        Options options = parse(args);
        if (active != null) {
            throw new CommandException("An ECS test is already running. Use /kirinoecs status");
        }
        Minecraft client = Minecraft.getMinecraft();
        if (client.world == null || client.player != sender) {
            throw new CommandException("Join a world before running the ECS test");
        }
        active = new Run(sender, client.world, options);
        chat(sender, "Collecting " + BASELINE_FRAMES + " baseline frames, then running " + options.scenarios
            + " (entities=" + options.rows + ", workers=" + options.workers + ")");
    }

    static Options parse(String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3) {
            throw new WrongUsageException(USAGE);
        }
        List<EcsDebugSuite.Scenario> scenarios;
        if (args[0].equals("all")) {
            scenarios = List.of(EcsDebugSuite.Scenario.values());
        } else {
            try {
                scenarios = List.of(EcsDebugSuite.Scenario.valueOf(args[0].toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException failure) {
                throw new WrongUsageException(USAGE);
            }
        }
        int rows = args.length > 1 ? parseInt(args[1], 1, MAX_ROWS) : DEFAULT_ROWS;
        int workers = args.length > 2 ? parseInt(args[2], 1, MAX_WORKERS)
            : Math.min(4, Runtime.getRuntime().availableProcessors());
        return new Options(scenarios, rows, workers);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Run run = active;
        if (run == null || event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft client = Minecraft.getMinecraft();
        boolean sameWorld = client.world == run.world && client.player == run.sender;
        if (!sameWorld && !run.started) {
            active = null;
            KirinoCommonCore.LOGGER.info("[ECS test] World changed before the test started");
            return;
        }

        long now = System.nanoTime();
        if (sameWorld && run.lastFrame != 0) {
            long elapsed = now - run.lastFrame;
            if (run.started) {
                run.during.add(elapsed);
            } else {
                run.baseline.add(elapsed);
            }
        }
        run.lastFrame = sameWorld ? now : 0;
        if (!run.started && run.baseline.size == BASELINE_FRAMES) {
            start(run);
        } else if (run.finished.isDone()) {
            active = null;
            finish(run, sameWorld);
        }
    }

    private void start(Run run) {
        run.started = true;
        run.status = "ECS test running";
        Thread.ofPlatform().daemon().name("Kirino ECS debug suite").start(() -> {
            List<EcsDebugSuite.Result> results = new ArrayList<>();
            try {
                EcsDebugSuite.run(runtime.get(), run.options.scenarios, run.options.rows, run.options.workers, result -> {
                    results.add(result);
                    run.status = "Completed " + results.size() + "/" + run.options.scenarios.size() + " scenarios";
                    KirinoCommonCore.LOGGER.info("[ECS test] {}", formatResult(result));
                });
                run.finished.complete(results.size());
            } catch (Throwable failure) {
                KirinoCommonCore.LOGGER.error("[ECS test] Failed after {} completed scenarios", results.size(), failure);
                run.finished.completeExceptionally(failure);
            }
        });
    }

    private static void finish(Run run, boolean sameWorld) {
        String frames = "Frame time: baseline " + run.baseline.summary() + " | during " + run.during.summary();
        KirinoCommonCore.LOGGER.info("[ECS test] {}", frames);
        try {
            int completed = run.finished.join();
            KirinoCommonCore.LOGGER.info("[ECS test] PASS ({}/{} scenarios, entities={}, workers={})", completed,
                run.options.scenarios.size(), run.options.rows, run.options.workers);
            if (sameWorld) {
                chat(run.sender, "PASS (" + completed + " scenarios). Per-scenario metrics are in latest.log");
                chat(run.sender, frames);
            }
        } catch (CompletionException failure) {
            if (sameWorld) {
                chat(run.sender, "FAIL: " + failure.getCause() + ". See latest.log for the stack trace");
            }
        }
    }

    private static String formatResult(EcsDebugSuite.Result result) {
        String allocation = result.allocatedBytes() < 0 ? "unavailable" : result.allocatedBytes() + " B";
        String lifecycle = result.scenario() == EcsDebugSuite.Scenario.LIFECYCLE
            ? (result.heldJobChecked() ? " | admitted async job: checked" : " | admitted async job: unavailable on this CPU") : "";
        return String.format(Locale.ROOT, "%s PASS | %.2f ms | allocated %s | flush x%d total %.2f ms max %.2f ms%s",
            result.scenario(), millis(result.elapsedNanos()), allocation, result.flushCount(), millis(result.flushNanos()),
            millis(result.maxFlushNanos()), lifecycle);
    }

    private static void chat(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString("[ECS test] " + message));
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    record Options(List<EcsDebugSuite.Scenario> scenarios, int rows, int workers) {
    }

    private static final class Run {
        private final ICommandSender sender;
        private final WorldClient world;
        private final Options options;
        private final FrameSamples baseline = new FrameSamples(BASELINE_FRAMES);
        private final FrameSamples during = new FrameSamples(MAX_SAMPLES);
        private final CompletableFuture<Integer> finished = new CompletableFuture<>();
        private volatile String status = "Collecting baseline frames";
        private boolean started;
        private long lastFrame;

        private Run(ICommandSender sender, WorldClient world, Options options) {
            this.sender = sender;
            this.world = world;
            this.options = options;
        }
    }

    private static final class FrameSamples {
        private final long[] samples;
        private int size;

        private FrameSamples(int capacity) {
            samples = new long[capacity];
        }

        private void add(long elapsed) {
            if (size < samples.length) {
                samples[size++] = elapsed;
            }
        }

        private String summary() {
            if (size == 0) {
                return "no samples";
            }
            Arrays.sort(samples, 0, size);
            return String.format(Locale.ROOT, "n=%d p50=%.2f ms p95=%.2f ms max=%.2f ms", size,
                millis(samples[(size - 1) / 2]), millis(samples[(95 * size - 1) / 100]), millis(samples[size - 1]));
        }
    }
}
