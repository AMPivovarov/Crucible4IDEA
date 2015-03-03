package com.jetbrains.crucible.ui.toolWindow.diff;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.SuppressiveDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.OnesideDiffTool;
import com.intellij.diff.tools.fragmented.OnesideDiffViewer;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.PopupHandler;
import com.jetbrains.crucible.actions.AddCommentAction;
import com.jetbrains.crucible.model.Comment;
import com.jetbrains.crucible.model.Review;
import com.jetbrains.crucible.utils.CrucibleBundle;
import com.jetbrains.crucible.utils.CrucibleUserDataKeys;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: ktisha
 */
public class OnesideCommentsDiffTool implements FrameDiffTool, SuppressiveDiffTool {
  @NotNull
  @Override
  public String getName() {
    return OnesideDiffTool.INSTANCE.getName();
  }

  @Override
  public List<Class<? extends DiffTool>> getSuppressedTools() {
    return Collections.<Class<? extends DiffTool>>singletonList(OnesideDiffTool.class);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (context.getUserData(CrucibleUserDataKeys.REVIEW) == null) return false;
    if (request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) == null) return false;
    return OnesideDiffViewer.canShowRequest(context, request);
  }

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyOnesideDiffViewer(context, request);
  }

  private static class MyOnesideDiffViewer extends OnesideDiffViewer {
    private final TIntFunction myLineToEditor = new TIntFunction() {
      @Override
      public int execute(int line) {
        return transferLineToOneside(Side.RIGHT, line);
      }
    };

    private final TIntFunction myLineFromEditor = new TIntFunction() {
      @Override
      public int execute(int line) {
        return transferLineFromOneside(line).first[1];
      }
    };

    private final List<RangeHighlighter> myCommentHighlighters = new ArrayList<RangeHighlighter>();

    public MyOnesideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
      super(context, request);
    }

    @Override
    protected void onInit() {
      super.onInit();

      final Review review = myContext.getUserData(CrucibleUserDataKeys.REVIEW);
      final Change change = myRequest.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
      assert review != null && change != null;

      final FilePath path = ChangesUtil.getFilePath(change);

      addCommentAction(myEditor, review, path);
    }

    @NotNull
    @Override
    protected Runnable performRediff(@NotNull ProgressIndicator indicator) {
      final Runnable runnable = super.performRediff(indicator);
      return new Runnable() {
        @Override
        public void run() {
          destroyComments();
          runnable.run();
          installComments();
        }
      };
    }

    private void destroyComments() {
      for (RangeHighlighter highlighter : myCommentHighlighters) {
        highlighter.dispose();
      }
      myCommentHighlighters.clear();
    }

    private void installComments() {
      final Review review = myContext.getUserData(CrucibleUserDataKeys.REVIEW);
      final Change change = myRequest.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
      assert review != null && change != null;

      final FilePath path = ChangesUtil.getFilePath(change);
      ContentRevision revision = change.getAfterRevision();
      if (revision == null) {
        revision = change.getBeforeRevision();
      }

      addGutter(myEditor, review, path, revision);
    }

    private void addCommentAction(@NotNull final Editor editor,
                                  @NotNull final Review review,
                                  @NotNull final FilePath filePath) {
      final AddCommentAction addCommentAction =
        new AddCommentAction(review, editor, filePath, CrucibleBundle.message("crucible.add.comment"), false,
                             myLineToEditor, myLineFromEditor);
      addCommentAction.setContextComponent(editor.getComponent());

      DefaultActionGroup group = new DefaultActionGroup(addCommentAction);
      PopupHandler.installUnknownPopupHandler(editor.getContentComponent(), group, ActionManager.getInstance());
    }

    private void addGutter(@NotNull Editor editor,
                           @NotNull final Review review,
                           @NotNull FilePath filePath,
                           @Nullable final ContentRevision revision) {
      if (revision == null) return;

      for (Comment comment : review.getComments()) {
        final String id = comment.getReviewItemId();
        final String path = review.getPathById(id);
        if (path != null && filePath.getPath().endsWith(path) &&
            (review.isInPatch(comment) || revision.getRevisionNumber().asString().equals(comment.getRevision()))) {

          myCommentHighlighters.add(AddCommentAction.addCommentHighlighter(editor, comment, review, filePath, myLineToEditor));
        }
      }
    }
  }
}
