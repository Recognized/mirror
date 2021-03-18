package mirror.misc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

import mirror.FileWatcher;
import mirror.Update;
import mirror.WatchServiceFileWatcher;
import mirror.tasks.TaskFactory;
import mirror.tasks.ThreadBasedTaskFactory;

/** Tests how quickly creating MD5s of a directory tree would take. */
public class Digest {

  public static void main(String[] args) throws Exception {
    Path root = Paths.get("/home/stephen/linkedin");
    TaskFactory taskFactory = new ThreadBasedTaskFactory();
    BlockingQueue<Update> queue = new ArrayBlockingQueue<>(1_000_000);
    WatchService watchService = FileSystems.getDefault().newWatchService();
    final Stopwatch s = Stopwatch.createStarted();
    FileWatcher r = new WatchServiceFileWatcher(taskFactory, watchService, root, queue);
    List<Update> initial = r.performInitialScan();
    s.stop();

    System.out.println("scan took " + s.elapsed(TimeUnit.MILLISECONDS) + " millis");
    s.reset();
    s.start();

    StringBuilder b = new StringBuilder();
    for (Update update : initial) {
      Path p = root.resolve(update.getPath());
      if (Files.exists(p) && Files.isRegularFile(p)) {
        b.append(getHash(p));
      }
    }
    s.stop();

    System.out.println("hash took " + s.elapsed(TimeUnit.MILLISECONDS) + " millis");
  }

  public static String getHash(Path path) throws IOException {
    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
      MessageDigest md = MessageDigest.getInstance("MD5");
      // via naive trial/error, 1024 is better than larger values like 2048 or 8192
      ByteBuffer bbf = ByteBuffer.allocateDirect(1024);
      int b = fc.read(bbf);
      while ((b != -1) && (b != 0)) {
        bbf.flip();
        md.update(bbf);
        b = fc.read(bbf);
      }
      fc.close();
      return toHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      sb.append(Integer.toHexString(0xFF & bytes[i]));
    }
    return sb.toString();
  }

}
