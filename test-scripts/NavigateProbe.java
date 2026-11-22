///usr/bin/env java "$0" "$@" ; exit $?
// Standalone Java code (not part of main project) - replaces bash/python/batch scripts with IDE-friendly, maintainable code using JDK 11/21/25 enhancements. To know why, refer to Cay Horstmann's JavaOne 2025 talk "Java for Small Coding Tasks" (https://youtu.be/04wFgshWMdA)
//
// Exercises the Navigate decision table against the built shaded jar:
//   java --enable-native-access=ALL-UNNAMED -cp target\<shaded>.jar test-scripts\NavigateProbe.java <dir> [--adapter]

import littlejlib.proj.*;

import java.nio.file.Path;
import java.util.List;

public class NavigateProbe {
    public static void main(String[] args) {
        StdoutChannel.protect();
        var opts = List.of(args);
        var dir = args.length > 0 && !args[0].startsWith("--") ? args[0] : ".";
        if (opts.contains("--adapter")) Navigate.adapterReading(true);

        System.err.println("JR_LAUNCH_MODE = " + System.getenv("JR_LAUNCH_MODE"));
        System.err.println("hasConsole     = " + LaunchMode.hasConsole());
        System.err.println("stdoutCaptured = " + LaunchMode.stdoutCaptured());
        System.err.println("shell          = " + Shell.detect());
        System.err.println("cdCommand      = " + Shell.cdCommand(Path.of(dir)));
        System.err.println("-- Navigate.to --");
        Navigate.to(Path.of(dir));
    }
}
