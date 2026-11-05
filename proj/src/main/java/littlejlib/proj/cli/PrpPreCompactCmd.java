package littlejlib.proj.cli;

import littlejlib.proj.precompact.PreCompactHook;
import littlejlib.proj.precompact.PreCompactWorker;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The command of the PreCompact hook (matcher {@code auto}): hook JSON on stdin, exit 2 blocks the compaction. */
@Command(name = "precompact", mixinStandardHelpOptions = true,
        description = "PreCompact hook: block an automatic compaction, update the PRP status, save logs, hibernate the tab")
public final class PrpPreCompactCmd implements Runnable {

    @Option(names = "--worker", hidden = true, description = "Internal: the detached job of this session id")
    String worker;

    @Override
    public void run() {
        if (worker != null) {
            PreCompactWorker.run(worker);
            return;
        }
        System.exit(PreCompactHook.run(System.in));
    }
}
