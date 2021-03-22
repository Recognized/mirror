package mirror;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class NativeFileAccessUtils {

  @VisibleForTesting
  public static void setModifiedTimeForSymlink(Path absolutePath, long millis) throws IOException {
    Files.setLastModifiedTime(absolutePath, FileTime.fromMillis(millis));
  }

  public static void setReadOnly(Path absolutePath) throws IOException {
    if (!absolutePath.toFile().setReadOnly()) {
      throw new IOException("Failed to set write permissions.");
    }
  }

  public static void setWritable(Path absolutePath) throws IOException {
    if (!absolutePath.toFile().setWritable(true)) {
      throw new IOException("Failed to set write permissions.");
    }
  }

  public static boolean isExecutable(Path absolutePath) throws IOException {
    Set<PosixFilePermission> p = Files.getPosixFilePermissions(absolutePath);
    return p.contains(PosixFilePermission.GROUP_EXECUTE)
            || p.contains(PosixFilePermission.OWNER_EXECUTE)
            || p.contains(PosixFilePermission.OTHERS_EXECUTE);
  }

  public static void setExecutable(Path absolutePath) throws IOException {
    Set<PosixFilePermission> p = Files.getPosixFilePermissions(absolutePath);
    p.add(PosixFilePermission.OWNER_EXECUTE);
    Files.setPosixFilePermissions(absolutePath, p);
  }
}
