package littlejlib.proj.daemon;

import java.nio.file.Files;

public class SmokeLauncher {

    public static void main(String[] args) throws Exception {
        var exe = Files.createTempFile("proj", ".exe");
        System.setProperty(Launcher.JR_PROP + "exe", exe.toString());
        var baked = new String[] {"--enable-native-access=ALL-UNNAMED", "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
                "-Xmx2g", "-XX:+UseG1GC", "-XX:+DisplayVMOutputToStderr"};
        for (var i = 0; i < baked.length; i++) System.setProperty(Launcher.JR_PROP + "jvm.vmArgs." + i, baked[i]);

        var cmd = Launcher.command();
        check(cmd.size() == 3 && cmd.get(0).equals(exe.toString()) && cmd.get(2).equals("--daemon"), "exe, vm.args, --daemon: " + cmd);
        var vm = cmd.get(1);
        check(vm.startsWith("-Xjr:vm.args="), "vm.args option: " + vm);
        check(vm.contains("--add-opens java.base/sun.nio.ch=ALL-UNNAMED"), "baked flags kept in order: " + vm);
        check(vm.contains("-XX:+DisplayVMOutputToStderr"), "baked non-memory flag kept: " + vm);
        check(!vm.contains("-Xmx2g") && !vm.contains("UseG1GC"), "baked memory flags dropped: " + vm);
        Launcher.MEMORY_CAPS.forEach(cap -> check(vm.contains(cap), "cap " + cap));

        Files.delete(exe);
        System.out.println("ok");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
