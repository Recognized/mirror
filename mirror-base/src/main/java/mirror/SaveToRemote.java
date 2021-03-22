package mirror;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import mirror.tasks.TaskLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

import static mirror.Utils.abbreviatePath;
import static mirror.Utils.debugString;

public class SaveToRemote implements TaskLogic {

  private static final Logger log = LoggerFactory.getLogger(SaveToRemote.class);
  private final FileAccess fileAccess;
  private final BlockingQueue<Update> results;
  private final OutgoingConnection outgoingChanges;

  public SaveToRemote(Queues queues, FileAccess fileAccess, OutgoingConnection outgoingChanges) {
    this.fileAccess = fileAccess;
    this.results = queues.saveToRemote;
    this.outgoingChanges = outgoingChanges;
  }

  @Override
  public Duration runOneLoop() throws InterruptedException {
    Update u = results.take();
    try {
      sendToRemote(u);
    } catch (RuntimeException e) {
      log.error("Exception with results " + u, e);
    }
    return null;
  }

  @VisibleForTesting
  void drain() throws Exception {
    while (!results.isEmpty()) {
      sendToRemote(results.take());
    }
  }

  private void sendToRemote(Update update) {
    try {
      Update.Builder b = Update.newBuilder(update).setLocal(false);
      if (!update.getDirectory() && update.getSymlink().isEmpty() && !update.getDelete() && b.getData() == ByteString.EMPTY) {
        b.setData(fileAccess.read(Paths.get(update.getPath())));
      }
      if (log.isInfoEnabled()) {
        StringBuilder s = new StringBuilder();
        s.append("Sending ");
        if (update.getDirectory()) {
          s.append("dir ");
        }
        if (update.getDelete()) {
          s.append("(delete) ");
        }
        s.append(abbreviatePath(update.getPath())).append(" ");
        if (!update.getDirectory()) {
          s.append(update.getData().size()).append(" bytes");
        }
        log.info(s.toString());
      }
      outgoingChanges.send(b.build());
    } catch (FileNotFoundException e) {
      // the file was very transient, which is fine, just drop it.
    } catch (IOException e) {
      // should we error here, so that the session is restarted?
      log.error("Could not read " + debugString(update), e);
    }
  }

}
