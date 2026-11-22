///usr/bin/env jbang "$0" "$@" ; exit $?
// Standalone Java code (not part of main project) - replaces bash/python/batch scripts with IDE-friendly, maintainable code using JDK 11/21/25 enhancements. To know why, refer to Cay Horstmann's JavaOne 2025 talk "Java for Small Coding Tasks" (https://youtu.be/04wFgshWMdA)

//DEPS info.picocli:picocli:4.7.7

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "test",
         description = "Test Picocli Help",
         version = "1.0",
         mixinStandardHelpOptions = false,
         subcommands = {
             TestPicocliHelp.SubCmd1.class,
             TestPicocliHelp.SubCmd2.class,
             TestPicocliHelp.HelpCmd.class
         })
public class TestPicocliHelp implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TestPicocliHelp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Main command executed - no subcommand provided");
    }

    @Command(name = "open", aliases = {"o"}, description = "Open something")
    static class SubCmd1 implements Runnable {
        @Override
        public void run() {
            System.out.println("Open command executed!");
        }
    }

    @Command(name = "help", aliases = {"h"}, description = "Show help")
    static class HelpCmd implements Runnable {
        @Override
        public void run() {
            System.out.println("=== HELP HEADER ===");
            CommandLine.usage(new TestPicocliHelp(), System.out);
            System.out.println("=== HELP FOOTER ===");
        }
    }

    @Command(name = "new", aliases = {"n"}, description = "Create something new")
    static class SubCmd2 implements Runnable {
        @Override
        public void run() {
            System.out.println("New command executed!");
        }
    }
}
