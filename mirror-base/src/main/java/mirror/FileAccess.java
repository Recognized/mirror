package mirror;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import com.google.protobuf.ByteString;

/**
 * An interfacing for reads/writes.
 * 
 * This is facilitates testing by using the {@link StubFileAccess}.
 */
public interface FileAccess {

  ByteString read(Path relativePath) throws IOException;

  void mkdir(Path relativePath) throws IOException;

  void write(Path relativePath, ByteBuffer data) throws IOException;

  void delete(Path relativePath) throws IOException;

  long getFileSize(Path relativePath) throws IOException;

  long getModifiedTime(Path relativePath) throws IOException;

  void setModifiedTime(Path relativePath, long time) throws IOException;

  boolean exists(Path relativePath);

  boolean isSymlink(Path relativePath);

  boolean isDirectory(Path relativePath);

  boolean isExecutable(Path relativePath) throws IOException;

  void setExecutable(Path relativePath) throws IOException;

  Path readSymlink(Path readSymlink) throws IOException;

  void createSymlink(Path relativePath, Path target) throws IOException;

}
