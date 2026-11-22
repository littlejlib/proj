package littlejlib.proj.ui;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

/// Draws the proj mark once, at every size, into the PNG resources the app loads; also assembles the Windows
/// exe icon (`src/native/rc/proj.ico`, PNG entries). Run: mvn exec:java -Dexec.mainClass=littlejlib.proj.ui.IconGen
/// -Dexec.classpathScope=test, from the proj folder.
public final class IconGen {
    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        var dir = Path.of("src/main/resources/littlejlib/proj/ui/icon");
        Files.createDirectories(dir);
        var proj = new ArrayList<byte[]>();
        for (var size : SIZES) {
            var p = png(AppIconArt.draw(size, AppIconArt.PROJECTS));
            Files.write(dir.resolve("proj-" + size + ".png"), p);
            Files.write(dir.resolve("prp-" + size + ".png"), png(AppIconArt.draw(size, AppIconArt.PRP)));
            proj.add(p);
        }
        var ico = Path.of("src/native/rc/proj.ico");
        Files.createDirectories(ico.getParent());
        Files.write(ico, ico(proj));
    }

    static byte[] png(java.awt.image.BufferedImage img) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static byte[] ico(List<byte[]> pngs) {
        var head = 6 + 16 * pngs.size();
        var buf = ByteBuffer.allocate(head + pngs.stream().mapToInt(b -> b.length).sum()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        var offset = head;
        for (var i = 0; i < pngs.size(); i++) {
            var size = SIZES[i] == 256 ? 0 : SIZES[i];
            buf.put((byte) size).put((byte) size).put((byte) 0).put((byte) 0)
                    .putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buf::put);
        return buf.array();
    }
}
