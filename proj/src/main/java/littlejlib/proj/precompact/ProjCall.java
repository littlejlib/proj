package littlejlib.proj.precompact;

import module java.base;

/** Runs {@code proj <args>} as its own process, so a System.exit inside a verb ends only that verb. */
final class ProjCall {

    private ProjCall() { }

    /** @return what the verb printed on stdout, or null when it failed or took over two minutes */
    static String run(Path dir, String... args) {
        var argv = new ArrayList<String>(List.of("proj"));
        argv.addAll(List.of(args));
        try {
            var p = new ProcessBuilder(argv).directory(dir.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            return p.exitValue() == 0 ? out : null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }
}
