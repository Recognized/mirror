package mirror;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.ArrayList;
import java.util.List;

import org.jooq.lambda.Seq;
import org.junit.Test;

import mirror.UpdateTree.Node;

public class UpdateTreeTest {

  private UpdateTree root = UpdateTree.newRoot();

  @Test
  public void addFileInRoot() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getName(), is("foo.txt"));
  }

  @Test
  public void addDirectoryInRoot() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getName(), is("foo"));
    assertThat(root.getChildren().get(0).getLocal().getDirectory(), is(true));
  }

  @Test
  public void addFileInSubDirectory() {
    root.addLocal(Update.newBuilder().setPath("bar").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("bar/foo.txt").build());
    assertThat(root.getChildren().size(), is(1));
    Node bar = root.getChildren().get(0);
    assertThat(bar.getChildren().size(), is(1));
    assertThat(bar.getChildren().get(0).getName(), is("foo.txt"));
  }

  @Test
  public void addFileInMissingSubDirectory() {
    // e.g. if bar/ was gitignored, but then bar/foo.txt is explicitly included,
    // we'll create a placeholder bar/ entry in the local/remote UpdateTree
    root.addLocal(Update.newBuilder().setPath("bar/foo.txt").build());
    assertThat(root.getChildren().size(), is(1));
    Node bar = root.getChildren().get(0);
    assertThat(bar.getName(), is("bar"));
    assertThat(bar.getPath(), is("bar"));
    assertThat(bar.getLocal(), is(nullValue()));
    assertThat(bar.getChildren().size(), is(1));
    assertThat(bar.getChildren().get(0).getName(), is("foo.txt"));
  }

  @Test
  public void addDirectoryInSubDirectory() {
    root.addLocal(Update.newBuilder().setPath("bar").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("bar/foo").setDirectory(true).build());
    assertThat(root.getChildren().size(), is(1));
    Node bar = root.getChildren().get(0);
    assertThat(bar.getChildren().size(), is(1));
    assertThat(bar.getChildren().get(0).getName(), is("foo"));
    assertThat(bar.getChildren().get(0).getLocal().getDirectory(), is(true));
  }

  @Test
  public void changeFileToADirecotry() {
    root.addLocal(Update.newBuilder().setPath("bar").build());
    root.addLocal(Update.newBuilder().setPath("bar").setDirectory(true).build());
    assertThat(root.getChildren().get(0).getLocal().getDirectory(), is(true));
  }

  @Test
  public void changeDirectoryToAFile() {
    root.addLocal(Update.newBuilder().setPath("bar").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("bar/sub").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("bar/sub/grand").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("bar").build());
    assertThat(root.find("bar").getLocal().getDirectory(), is(false));
    assertThat(root.find("bar/sub").getLocal().getDelete(), is(true));
    assertThat(root.find("bar/sub/grand").getLocal().getDelete(), is(true));
  }

  @Test
  public void addingTheRootDoesNotDuplicateIt() {
    root.addLocal(Update.newBuilder().setPath("").setModTime(1L).build());
    assertThat(root.getChildren(), is(nullValue()));
  }

  @Test
  public void deleteFileMarksTheNodeAsDeleted() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(1L).build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(true));
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1L));
  }

  @Test
  public void deleteSymlinkMarksTheNodeAsDeleted() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").setSymlink("bar").build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(true));
    assertThat(root.getChildren().get(0).getLocal().getSymlink(), is(""));
  }

  @Test
  public void deleteDirectoryMarksTheNodeAsDeletedAndChildren() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).build());
    root.addLocal(Update.newBuilder().setPath("foo/bar.txt").build());
    root.addLocal(Update.newBuilder().setPath("foo").setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.find("foo").getLocal().getDelete(), is(true));
    assertThat(root.find("foo/bar.txt").getLocal().getDelete(), is(true));
  }

  @Test
  public void deleteThenCreateFile() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(1L).build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(2L).setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(true));
    // now it's re-created
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(3L).build());
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(false));
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(3L));
  }

  @Test
  public void deleteThenRestoreFile() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(1L).build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(2L).setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(true));
    // now it's re-created but with the original timestamp
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(1L).build());
    assertThat(root.getChildren().get(0).getLocal().getDelete(), is(false));
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1002L));
  }

  @Test
  public void deleteFileTwiceDoesNotRetickModTime() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").setModTime(1L).build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").setDelete(true).build());
    assertThat(root.getChildren().size(), is(1));
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1L));
    root.addLocal(Update.newBuilder().setPath("foo.txt").setDelete(true).build());
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1L));
  }

  @Test
  public void ignoreFileSystemDirectoryModTimeTicks() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(1L).build());
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(2L).build());
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1L));
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsIfPathStartsWithSlash() {
    root.addLocal(Update.newBuilder().setPath("/foo").build());
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsIfPathEndsWithSlash() {
    root.addLocal(Update.newBuilder().setPath("foo/").build());
  }

  @Test
  public void visitDirtyNodes() {
    root.addLocal(Update.newBuilder().setPath("foo.txt").build());
    root.addLocal(Update.newBuilder().setPath("bar").build());
    root.addLocal(Update.newBuilder().setPath("bar/foo.txt").build());

    // on the first visit, we see all the nodes
    List<Node> nodes = new ArrayList<>();
    root.visitDirty(n -> nodes.add(n));
    assertThat(nodes.size(), is(4));

    // if no nodes change, then we don't visit any
    nodes.clear();
    root.visitDirty(n -> nodes.add(n));
    assertThat(nodes.size(), is(0));

    // if one node changes, we visit only that one
    root.addLocal(Update.newBuilder().setPath("foo.txt").build());
    nodes.clear();
    root.visitDirty(n -> nodes.add(n));
    assertThat(Seq.seq(nodes).map(n -> n.getPath()), contains("foo.txt"));

    // if a child node changes, we visit only that one
    root.addLocal(Update.newBuilder().setPath("bar/foo.txt").build());
    nodes.clear();
    root.visitDirty(n -> nodes.add(n));
    assertThat(Seq.seq(nodes).map(n -> n.getPath()), contains("bar/foo.txt"));
  }

  @Test
  public void ignoreFilesInRootByExtension() {
    root.addLocal(Update.newBuilder().setPath(".gitignore").setIgnoreString("*.txt").build());
    root.addLocal(Update.newBuilder().setPath("foo.txt").build());
    assertThat(find("foo.txt").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInChildByExtension() {
    root.addLocal(Update.newBuilder().setPath(".gitignore").setIgnoreString("*.txt").build());
    root.addLocal(Update.newBuilder().setPath("foo/bar.txt").build());
    assertThat(find("foo/bar.txt").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInChildByDirectory() {
    root.addLocal(Update.newBuilder().setPath(".gitignore").setIgnoreString("foo/").build());
    root.addLocal(Update.newBuilder().setPath("foo/bar.txt").build());
    assertThat(find("foo/bar.txt").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInRootByExtraExcludes() {
    root = newRoot(new PathRules(), new PathRules("build"));
    root.addLocal(Update.newBuilder().setPath("build").setDirectory(true).build());
    assertThat(find("build").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInChildByExtraExcludes() {
    root = newRoot(new PathRules(), new PathRules("build"));
    root.addLocal(Update.newBuilder().setPath("child/build").setDirectory(true).build());
    assertThat(find("child/build").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInRootByExtraExcludesWithPath() {
    root = newRoot(new PathRules(), new PathRules("build/classes"));
    root.addLocal(Update.newBuilder().setPath("build/classes/Foo.class").setDirectory(true).build());
    assertThat(find("build/classes/Foo.class").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesInChildByExtraExcludesWithPath() {
    // build/classes will not work since extra rules are always based on the root path,
    // but git supports **/build/classes as the syntax for "at any child node"
    root = newRoot(new PathRules(), new PathRules("**/build/classes"));
    root.addLocal(Update.newBuilder().setPath("child/build/classes/Foo.class").setDirectory(true).build());
    assertThat(find("child/build/classes/Foo.class").shouldIgnore(), is(true));
  }

  @Test
  public void ignoreFilesWithinIgnoredDirectory() {
    root.addLocal(Update.newBuilder().setPath(".gitignore").setIgnoreString("child/").build());
    root.addLocal(Update.newBuilder().setPath("child/foo.txt").setDirectory(true).build());
    assertThat(find("child/foo.txt").shouldIgnore(), is(true));
  }

  @Test
  public void isNewerForDirectoriesDoesNotCareAboutModTime() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(1).build());
    root.addRemote(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(2).build());
    assertThat(root.getChildren().get(0).isLocalNewer(), is(false));
    assertThat(root.getChildren().get(0).isRemoteNewer(), is(false));
  }

  @Test
  public void isNewerForDeletedDirectoriesDoesCareAboutModTime() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(3).build());
    root.addRemote(Update.newBuilder().setPath("foo").setDirectory(true).setDelete(true).setModTime(2).build());
    assertThat(root.getChildren().get(0).isLocalNewer(), is(true));
    assertThat(root.getChildren().get(0).isRemoteNewer(), is(false));
  }

  @Test
  public void isNewerForDeletedDirectoryDoesCareAboutModTime() {
    root.addLocal(Update.newBuilder().setPath("foo").setDirectory(true).setDelete(true).setModTime(2).build());
    root.addRemote(Update.newBuilder().setPath("foo").setDirectory(true).setModTime(1).build());
    assertThat(root.getChildren().get(0).isLocalNewer(), is(true));
  }

  @Test
  public void isNewerForDeletedFile() {
    root.addLocal(Update.newBuilder().setPath("foo").setModTime(1).build());
    root.addRemote(Update.newBuilder().setPath("foo").setModTime(1).build());
    root.addLocal(Update.newBuilder().setPath("foo").setDelete(true).build());
    assertThat(root.getChildren().get(0).isLocalNewer(), is(true));
  }

  @Test
  public void isNewerForRestoredFiles() {
    // given we'd marked foo as deleted
    root.addLocal(Update.newBuilder().setPath("foo").setDelete(true).setModTime(1).build());
    root.addRemote(Update.newBuilder().setPath("foo").setDelete(true).setModTime(1).build());
    // when a new non-delete comes in with the same modtime (i.e. a `mv` of the old file)
    root.addLocal(Update.newBuilder().setPath("foo").setModTime(1).build());
    // then we've artificially ticked the modtime ahead so we can recognize it as newer
    assertThat(root.getChildren().get(0).getLocal().getModTime(), is(1001L));
    assertThat(root.getChildren().get(0).isLocalNewer(), is(true));
    assertThat(root.getChildren().get(0).isRemoteNewer(), is(false));
  }

  @Test
  public void isNewerForCorruptModTimes() {
    long now = System.currentTimeMillis();
    long tooFarInTheFuture =  now * 2;
    root.addLocal(Update.newBuilder().setPath("foo").setModTime(now).build());
    root.addRemote(Update.newBuilder().setPath("foo").setModTime(tooFarInTheFuture).build());
    assertThat(root.getChildren().get(0).isLocalNewer(), is(true));
  }

  Node find(String path) {
    return root.find(path);
  }

  private static UpdateTree newRoot(PathRules includes, PathRules excludes) {
    return UpdateTree.newRoot(new MirrorPaths(null, null, new PathRules(), new PathRules("build"), false, new ArrayList<>()));
  }
}
