package mirror;

import static mirror.Utils.abbreviatePath;
import static mirror.Utils.debugString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import mirror.tasks.TaskLogic;

public class SaveToLocal implements TaskLogic {

  private static final Logger log = LoggerFactory.getLogger(SaveToLocal.class);
  private final BlockingQueue<Update> results;
  private final FileAccess fileAccess;

  public SaveToLocal(Queues queues, FileAccess fileAccess) {
    this.results = queues.saveToLocal;
    this.fileAccess = fileAccess;
  }

  @Override
  public Duration runOneLoop() throws InterruptedException {
    Update u = results.take();
    try {
      saveLocally(u);
    } catch (RuntimeException e) {
      log.error("Exception with results " + u, e);
    }
    return null;
  }

  @VisibleForTesting
  void drain() throws Exception {
    while (!results.isEmpty()) {
      saveLocally(results.take());
    }
  }

  private void saveLocally(Update remote) {
    try {
      if (remote.getDelete()) {
        deleteLocally(remote);
      } else if (!remote.getSymlink().isEmpty()) {
        saveSymlinkLocally(remote);
      } else if (remote.getDirectory()) {
        createDirectoryLocally(remote);
      } else {
        saveFileLocally(remote);
      }
    } catch (IOException e) {
      log.error("Error saving " + debugString(remote), e);
    }
  }

  // Note that this will generate a new local delete event (because we should
  // only be doing this when we want to immediately re-create the path as a
  // different type, e.g. a file -> a directory), but we end up ignoring
  // this stale delete event with isStaleLocalUpdate
  private void deleteLocally(Update remote) throws IOException {
    log.info("Remote delete {}", abbreviatePath(remote.getPath()));
    Path path = Paths.get(remote.getPath());
    fileAccess.delete(path);
  }

  private void saveSymlinkLocally(Update remote) throws IOException {
    log.info("Remote symlink {}", abbreviatePath(remote.getPath()));
    Path path = Paths.get(remote.getPath());
    Path target = Paths.get(remote.getSymlink());
    fileAccess.createSymlink(path, target);
    // this is going to trigger a local update, but since the write
    // doesn't go to the symlink, we think the symlink is changed
    fileAccess.setModifiedTime(path, remote.getModTime());
  }

  private void createDirectoryLocally(Update remote) throws IOException {
    log.info("Remote directory {}", abbreviatePath(remote.getPath()));
    Path path = Paths.get(remote.getPath());
    fileAccess.mkdir(path);
    fileAccess.setModifiedTime(path, remote.getModTime());
  }

  private void saveFileLocally(Update remote) throws IOException {
    log.info("Remote update {}", abbreviatePath(remote.getPath()));
    Path path = Paths.get(remote.getPath());
    if (remote.getData().equals(UpdateTree.initialSyncMarker)) {
      throw new IllegalStateException("Likely bug, did not expect sync marker");
    }
    ByteBuffer data = remote.getData().asReadOnlyByteBuffer();
    fileAccess.write(path, data);
    if (remote.getExecutable()) {
      fileAccess.setExecutable(path);
    }
    fileAccess.setModifiedTime(path, remote.getModTime());
  }

}
