package com.jetbrains.crucible.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.table.JBTable;
import com.jetbrains.crucible.model.Comment;
import com.jetbrains.crucible.model.Review;
import com.jetbrains.crucible.ui.toolWindow.details.CommentBalloonBuilder;
import com.jetbrains.crucible.ui.toolWindow.details.CommentForm;
import com.jetbrains.crucible.ui.toolWindow.details.CommentsTree;
import com.jetbrains.crucible.ui.toolWindow.details.GeneralCommentsTree;
import com.jetbrains.crucible.ui.toolWindow.diff.ReviewGutterIconRenderer;
import com.jetbrains.crucible.utils.CrucibleBundle;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * User: ktisha
 * <p/>
 * Reply to comment
 */
@SuppressWarnings("ComponentNotRegistered")
public class AddCommentAction extends AnActionButton implements DumbAware {

  private final Editor myEditor;
  private final Review myReview;
  private final boolean myIsReply;
  @Nullable private final FilePath myFilePath;
  @Nullable private final TIntFunction myLineToEditor;
  @Nullable private final TIntFunction myLineFromEditor;

  public AddCommentAction(@NotNull final Review review, @Nullable final Editor editor,
                          @Nullable FilePath filePath, @NotNull final String description,
                          boolean isReply) {
    this(review, editor, filePath, description, isReply, null, null);
  }

  public AddCommentAction(@NotNull final Review review, @Nullable final Editor editor,
                          @Nullable FilePath filePath, @NotNull final String description,
                          boolean isReply,
                          @Nullable TIntFunction lineToEditor, @Nullable TIntFunction lineFromEditor) {
    super(description, description, isReply ? IconLoader.getIcon("/images/comment_reply.png") :
                                              IconLoader.getIcon("/images/comment_add.png"));
    myFilePath = filePath;
    myIsReply = isReply;
    myReview = review;
    myEditor = editor;
    myLineToEditor = lineToEditor;
    myLineFromEditor = lineFromEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (toolWindow != null) {
      addGeneralComment(project, dataContext);
    }
    else {
      addVersionedComment(project);
    }
  }

  private void addGeneralComment(@NotNull final Project project, DataContext dataContext) {
    final CommentForm commentForm = new CommentForm(project, true, myIsReply, null, myLineFromEditor);
    commentForm.setReview(myReview);

    if (myIsReply) {
      final JBTable contextComponent = (JBTable)getContextComponent();
      final int selectedRow = contextComponent.getSelectedRow();
      if (selectedRow >= 0) {
        final Object parentComment = contextComponent.getValueAt(selectedRow, 0);
        if (parentComment instanceof Comment) {
          commentForm.setParentComment(((Comment)parentComment));
        }
      }
      else return;
    }

    final JBPopup balloon = CommentBalloonBuilder.getNewCommentBalloon(commentForm, myIsReply ? CrucibleBundle
      .message("crucible.new.reply.$0", commentForm.getParentComment().getPermId()) :
                                                                                    CrucibleBundle
                                                                                      .message("crucible.new.comment.$0",
                                                                                               myReview.getPermaId()));
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if(!commentForm.getText().isEmpty()) { // do not try to save draft if text is empty
          commentForm.postComment();
        }
        final JComponent component = getContextComponent();
        if (component instanceof GeneralCommentsTree) {
          ((GeneralCommentsTree)component).refresh();
        }
      }
    });

    commentForm.setBalloon(balloon);
    balloon.showInBestPositionFor(dataContext);
    commentForm.requestFocus();
  }

  private void addVersionedComment(@NotNull final Project project) {
    if (myEditor == null || myFilePath == null) return;
    final CommentForm commentForm = new CommentForm(project, false, myIsReply, myFilePath, myLineFromEditor);
    commentForm.setReview(myReview);

    final JComponent contextComponent = getContextComponent();
    if (myIsReply && contextComponent instanceof CommentsTree) {
      Comment comment = ((CommentsTree)contextComponent).getSelectedComment();
      assert comment != null;
      commentForm.setParentComment(comment);
    }
    commentForm.setEditor(myEditor);
    final JBPopup balloon = CommentBalloonBuilder.getNewCommentBalloon(commentForm, myIsReply ?
                                                                                    CrucibleBundle
                                                                                      .message("crucible.new.reply.$0", "Comment") :
                                                                                    CrucibleBundle
                                                                                      .message("crucible.new.comment.$0", myFilePath));
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        final Comment comment = commentForm.postComment();
        if (!myIsReply) {
          if (comment != null) {
            addCommentHighlighter(myEditor, comment, myReview, myFilePath, myLineToEditor);
          }
        }
        else {
          if (contextComponent instanceof CommentsTree) {
            final TreePath selectionPath = ((JTree)contextComponent).getSelectionPath();
            if (selectionPath == null) return;
            final Object component = selectionPath.getLastPathComponent();
            if (component instanceof DefaultMutableTreeNode && comment != null) {
              ((DefaultMutableTreeNode)component).add(new DefaultMutableTreeNode(comment));
              final TreeModel model = ((JTree)contextComponent).getModel();
              if (model instanceof DefaultTreeModel) {
                ((DefaultTreeModel)model).reload();
              }
            }
          }
        }
      }
    });
    commentForm.setBalloon(balloon);

    balloon.showInBestPositionFor(myEditor);
    commentForm.requestFocus();
  }

  @Override
  public boolean isEnabled() {
    //if (myIsReply && myEditor == null) {
    //  final JBTable contextComponent = (JBTable)getContextComponent();
    //  final int selectedRow = contextComponent.getSelectedRow();
    //  if (selectedRow < 0) {
    //    return false;
    //  }
    //}
    return true;
  }

  @NotNull
  public static RangeHighlighter addCommentHighlighter(@NotNull Editor editor,
                                           @NotNull Comment comment,
                                           @NotNull Review review,
                                           @NotNull FilePath filePath) {
    return addCommentHighlighter(editor, comment, review, filePath, null);
  }

  @NotNull
  public static RangeHighlighter addCommentHighlighter(@NotNull Editor editor,
                                                       @NotNull Comment comment,
                                                       @NotNull Review review,
                                                       @NotNull FilePath filePath,
                                                       @Nullable TIntFunction lineToEditor) {
    int line = Integer.parseInt(comment.getLine()) - 1;
    if (lineToEditor != null) line = lineToEditor.execute(line);
    final RangeHighlighter highlighter = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
    final ReviewGutterIconRenderer gutterIconRenderer = new ReviewGutterIconRenderer(review, filePath, comment);
    highlighter.setGutterIconRenderer(gutterIconRenderer);
    return highlighter;
  }
}
