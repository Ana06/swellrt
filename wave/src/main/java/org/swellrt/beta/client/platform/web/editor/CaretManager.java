package org.swellrt.beta.client.platform.web.editor;

import org.swellrt.beta.common.SException;
import org.swellrt.beta.model.SEvent;
import org.swellrt.beta.model.SMap;
import org.swellrt.beta.model.SMutationHandler;
import org.waveprotocol.wave.client.common.util.JsoView;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent;
import org.waveprotocol.wave.client.editor.EditorUpdateEvent.EditorUpdateListener;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.model.document.MutableAnnotationSet;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Carets positions are stored in a transient map (in the transient wavelet) by
 * this class when editor's selection changes.
 * <p>
 * <br>
 * When transient map is updated, a local annotation is set to represent the
 * caret within the document. This annotation is of type
 * {@link CaretAnnotationConstants.USER_END}.
 * <p>
 * <br>
 * Carets are eventually rendered by annotation painters configured in
 * {@link CaretAnnotationHandler}.
 * <p>
 * <br>
 * Currently only live carets are managed. Live selections are not activated as
 * long as they don't seem a practical feature. See
 * {@link CaretAnnotationConstants.USER_RANGE} for more info.
 *
 *
 */
public class CaretManager implements EditorUpdateListener {


  private final SMutationHandler caretsListener = new SMutationHandler() {

    @Override
    public boolean exec(SEvent e) {

      if (e.isAddEvent() || e.isUpdateEvent()) {

        CaretInfo caretInfo = (CaretInfo) e.getValue();

        if (!caretInfo.getParticipant().equals(participantId.getAddress()))
          updateAnnotations(caretInfo);
      }

      return false;
    }
  };

  private final ParticipantId participantId;
  private final String sessionId;
  private final SMap carets;
  private final Editor editor;

  public CaretManager(ParticipantId participantId, String sessionId, SMap carets,
      Editor editor) {
    this.participantId = participantId;
    this.sessionId = sessionId;
    this.carets = carets;
    this.editor = editor;

  }

  public void start() {
    editor.addUpdateListener(this);
    try {
      carets.listen(caretsListener);
    } catch (SException e) {
      throw new IllegalStateException(e);
    }
  }

  public void stop() {
    try {
      carets.unlisten(caretsListener);
    } catch (SException e) {
      throw new IllegalStateException(e);
    }
    editor.removeUpdateListener(this);
  }

  /**
   * Update local annotations in the document according to provided caret info.
   *
   * @param caretInfo
   *          info about the caret to update annotations.
   */
  private void updateAnnotations(CaretInfo caretInfo) {

    ContentDocument content = editor.getContent();
    MutableAnnotationSet.Local annotations = content.getLocalAnnotations();

    updateCaretAnnotation(content, annotations, caretInfo);

    // updateSelectionAnnotation(content, annotations, caretInfo);
  }

  /**
   * Update the annotation that marks the caret position
   * ({@link CaretAnnotationConstants.USER_END}) for the session of a
   * {@link CaretInfo}
   *
   * @param content
   * @param annotations
   * @param caretInfo
   */
  private void updateCaretAnnotation(ContentDocument content,
      MutableAnnotationSet.Local annotations, CaretInfo caretInfo) {

    String key = CaretAnnotationConstants.endKey(caretInfo.getSession());
    String value = caretInfo.getParticipant();

    int size = content.getMutableDoc().size();
    int currentFocusPos = annotations.firstAnnotationChange(0, size, key, null);
    int newFocusPos = caretInfo.getPosition();

    if (currentFocusPos == -1) {
      // New USER_END annotation
      annotations.setAnnotation(newFocusPos, size, key, value);

    } else {
      // Update annotation

      if (newFocusPos < currentFocusPos) {
        // New caret is before the current one. Extend the annotation.
        annotations.setAnnotation(newFocusPos, currentFocusPos, key, value);

      } else if (newFocusPos > currentFocusPos) {
        // New caret is after current, remove annotation in before the caret
        annotations.setAnnotation(currentFocusPos, newFocusPos, key, null);
      }

    }

  }

  /**
   * Update the annotation that marks the text selected
   * ({@link CaretAnnotationConstants.USER_RANGE}) by the provided caret info.
   *
   * TODO tbc
   *
   * @param content
   * @param annotations
   * @param caretInfo
   */
  @SuppressWarnings("unused")
  private void updateSelectionAnnotation(ContentDocument content,
      MutableAnnotationSet.Local annotations, CaretInfo caretInfo) {

    String rangeKey = CaretAnnotationConstants.rangeKey(caretInfo.getSession());
    String value = caretInfo.getParticipant();

    int size = content.getMutableDoc().size();
    int currentStart = annotations.firstAnnotationChange(0, size, rangeKey, null);
    int currentEnd = annotations.lastAnnotationChange(0, size, rangeKey, null);

    // TODO to be completed

  }

  @Override
  public void onUpdate(EditorUpdateEvent event) {
    FocusedRange selection = event.context().getSelectionHelper().getSelectionRange();

    if (event.selectionLocationChanged() && selection != null) {

      int caretPos = selection.asRange().getStart();

      CaretInfo caretInfo = buildCaretInfo(participantId.getAddress(), sessionId, caretPos,
          System.currentTimeMillis());

      try {
        carets.put(sessionId, caretInfo);
      } catch (SException e) {
        throw new IllegalStateException(e);
      }
    }

  }

  private static CaretInfo buildCaretInfo(String participantId, String sessionId, int caretPos,
      long lastUpdateTime) {

    JsoView caretInfo = JsoView.create();
    caretInfo.setNumber("timestamp", lastUpdateTime);
    caretInfo.setString("participant", participantId);
    caretInfo.setString("session", sessionId);
    caretInfo.setNumber("position", caretPos);

    return caretInfo.cast();

  }

}