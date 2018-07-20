/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.ActivityLogger;
import org.odk.collect.android.exception.GDriveConnectionException;
import org.odk.collect.android.exception.JavaRosaException;
import org.odk.collect.android.listeners.AudioPlayListener;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.preferences.GeneralSharedPreferences;
import org.odk.collect.android.preferences.GuidanceHint;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.utilities.ActivityResultHelper;
import org.odk.collect.android.utilities.AnimateUtils;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.DependencyProvider;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.FormEntryPromptUtils;
import org.odk.collect.android.utilities.ImageConverter;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.SoftKeyboardUtils;
import org.odk.collect.android.utilities.TextUtils;
import org.odk.collect.android.utilities.ThemeUtils;
import org.odk.collect.android.utilities.ToastUtils;
import org.odk.collect.android.utilities.ViewIds;
import org.odk.collect.android.views.MediaLayout;
import org.odk.collect.android.widgets.interfaces.BinaryWidget;
import org.odk.collect.android.widgets.interfaces.ButtonWidget;
import org.odk.collect.android.widgets.interfaces.Widget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import timber.log.Timber;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static org.odk.collect.android.activities.FormEntryActivity.DO_NOT_EVALUATE_CONSTRAINTS;

public abstract class QuestionWidget
        extends RelativeLayout
        implements Widget, AudioPlayListener {

    private static final String GUIDANCE_EXPANDED_STATE = "expanded_state";

    private final int questionFontSize;
    private final FormEntryPrompt formEntryPrompt;
    private final MediaLayout questionMediaLayout;
    private final TextView helpTextView;
    private final TextView guidanceTextView;
    private final View helpTextLayout;
    private final View guidanceTextLayout;
    private final View textLayout;
    private final TextView warningText;

    protected ThemeUtils themeUtils;

    private MediaPlayer player;
    private AtomicBoolean expanded;
    private Bundle state;
    private int playColor;

    public QuestionWidget(Context context, FormEntryPrompt prompt) {
        super(context);

        themeUtils = new ThemeUtils(context);
        playColor = themeUtils.getAccentColor();

        if (context instanceof FormEntryActivity) {
            state = ((FormEntryActivity) context).getState();
        }

        if (context instanceof DependencyProvider) {
            injectDependencies((DependencyProvider) context);
        }

        player = new MediaPlayer();
        getPlayer().setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                getQuestionMediaLayout().resetTextFormatting();
                mediaPlayer.reset();
            }

        });

        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Timber.e("Error occured in MediaPlayer. what = %d, extra = %d",
                        what, extra);
                return false;
            }
        });

        questionFontSize = Collect.getQuestionFontsize();

        formEntryPrompt = prompt;

        setGravity(Gravity.TOP);
        setPadding(0, 7, 0, 0);

        questionMediaLayout = createQuestionMediaLayout(prompt);
        helpTextLayout = createHelpTextLayout();
        helpTextLayout.setId(ViewIds.generateViewId());
        guidanceTextLayout = helpTextLayout.findViewById(R.id.guidance_text_layout);
        textLayout = helpTextLayout.findViewById(R.id.text_layout);
        warningText = helpTextLayout.findViewById(R.id.warning_text);
        helpTextView = setupHelpText(helpTextLayout.findViewById(R.id.help_text_view), prompt);
        guidanceTextView = setupGuidanceTextAndLayout(helpTextLayout.findViewById(R.id.guidance_text_view), prompt);

        addQuestionMediaLayout(getQuestionMediaLayout());
        addHelpTextLayout(getHelpTextLayout());
    }

    //source::https://stackoverflow.com/questions/18996183/identifying-rtl-language-in-android/23203698#23203698
    public static boolean isRTL() {
        return isRTL(Locale.getDefault());
    }

    private static boolean isRTL(Locale locale) {
        final int directionality = Character.getDirectionality(locale.getDisplayName().charAt(0));
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    private TextView setupGuidanceTextAndLayout(TextView guidanceTextView, FormEntryPrompt prompt) {

        TextView guidance = null;
        GuidanceHint setting = GuidanceHint.get((String) GeneralSharedPreferences.getInstance().get(PreferenceKeys.KEY_GUIDANCE_HINT));

        if (setting.equals(GuidanceHint.No)) {
            return null;
        }

        String guidanceHint = prompt.getSpecialFormQuestionText(prompt.getQuestion().getHelpTextID(), "guidance");

        if (android.text.TextUtils.isEmpty(guidanceHint)) {
            return null;
        }

        guidance = configureGuidanceTextView(guidanceTextView, guidanceHint);

        expanded = new AtomicBoolean(false);

        if (getState() != null) {
            if (getState().containsKey(GUIDANCE_EXPANDED_STATE + getFormEntryPrompt().getIndex())) {
                Boolean result = getState().getBoolean(GUIDANCE_EXPANDED_STATE + getFormEntryPrompt().getIndex());
                expanded = new AtomicBoolean(result);
            }
        }

        if (setting.equals(GuidanceHint.Yes)) {
            guidanceTextLayout.setVisibility(VISIBLE);
            guidanceTextView.setText(guidanceHint);
        } else if (setting.equals(GuidanceHint.YesCollapsed)) {
            guidanceTextLayout.setVisibility(expanded.get() ? VISIBLE : GONE);

            View icon = textLayout.findViewById(R.id.help_icon);
            icon.setVisibility(VISIBLE);

            /**
             * Added click listeners to the individual views because the TextView
             * intercepts click events when they are being passed to the parent layout.
             */
            icon.setOnClickListener(v -> {
                if (!expanded.get()) {
                    AnimateUtils.expand(guidanceTextLayout, result -> expanded.set(true));
                } else {
                    AnimateUtils.collapse(guidanceTextLayout, result -> expanded.set(false));
                }
            });

            getHelpTextView().setOnClickListener(v -> {
                if (!expanded.get()) {
                    AnimateUtils.expand(guidanceTextLayout, result -> expanded.set(true));
                } else {
                    AnimateUtils.collapse(guidanceTextLayout, result -> expanded.set(false));
                }
            });
        }

        return guidance;
    }

    private TextView configureGuidanceTextView(TextView guidanceTextView, String guidance) {
        guidanceTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getQuestionFontSize() - 3);
        //noinspection ResourceType
        guidanceTextView.setPadding(0, -5, 0, 7);
        // wrap to the widget of view
        guidanceTextView.setHorizontallyScrolling(false);
        guidanceTextView.setTypeface(null, Typeface.ITALIC);

        guidanceTextView.setText(TextUtils.textToHtml(guidance));

        guidanceTextView.setTextColor(themeUtils.getPrimaryTextColor());
        guidanceTextView.setMovementMethod(LinkMovementMethod.getInstance());
        return guidanceTextView;
    }

    /**
     * Releases resources held by this widget
     */
    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    protected void injectDependencies(DependencyProvider dependencyProvider) {
        //dependencies for the widget will be wired here.
    }

    private MediaLayout createQuestionMediaLayout(FormEntryPrompt prompt) {
        String promptText = prompt.getLongText();
        // Add the text view. Textview always exists, regardless of whether there's text.
        TextView questionText = new TextView(getContext());
        questionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getQuestionFontSize());
        questionText.setTypeface(null, Typeface.BOLD);
        questionText.setPadding(0, 0, 0, 7);
        questionText.setTextColor(themeUtils.getPrimaryTextColor());
        questionText.setText(TextUtils.textToHtml(FormEntryPromptUtils.markQuestionIfIsRequired(promptText, prompt.isRequired())));
        questionText.setMovementMethod(LinkMovementMethod.getInstance());

        // Wrap to the size of the parent view
        questionText.setHorizontallyScrolling(false);

        if ((promptText == null || promptText.isEmpty())
                && !(prompt.isRequired() && (prompt.getHelpText() == null || prompt.getHelpText().isEmpty()))) {
            questionText.setVisibility(GONE);
        }

        String imageURI = this instanceof SelectImageMapWidget ? null : prompt.getImageText();
        String audioURI = prompt.getAudioText();
        String videoURI = prompt.getSpecialFormQuestionText("video");

        // shown when image is clicked
        String bigImageURI = prompt.getSpecialFormQuestionText("big-image");

        // Create the layout for audio, image, text
        MediaLayout questionMediaLayout = new MediaLayout(getContext(), getPlayer());
        questionMediaLayout.setId(ViewIds.generateViewId()); // assign random id
        questionMediaLayout.setAVT(prompt.getIndex(), "", questionText, audioURI, imageURI, videoURI,
                bigImageURI);
        questionMediaLayout.setAudioListener(this);

        String playColorString = prompt.getFormElement().getAdditionalAttribute(null, "playColor");
        if (playColorString != null) {
            try {
                playColor = Color.parseColor(playColorString);
            } catch (IllegalArgumentException e) {
                Timber.e(e, "Argument %s is incorrect", playColorString);
            }
        }
        questionMediaLayout.setPlayTextColor(getPlayColor());

        return questionMediaLayout;
    }

    public TextView getHelpTextView() {
        return helpTextView;
    }

    public void playAudio() {
        playAllPromptText();
    }

    public void playVideo() {
        getQuestionMediaLayout().playVideo();
    }

    public FormEntryPrompt getFormEntryPrompt() {
        return formEntryPrompt;
    }

    // http://code.google.com/p/android/issues/detail?id=8488
    private void recycleDrawablesRecursive(ViewGroup viewGroup, List<ImageView> images) {

        int childCount = viewGroup.getChildCount();
        for (int index = 0; index < childCount; index++) {
            View child = viewGroup.getChildAt(index);
            if (child instanceof ImageView) {
                images.add((ImageView) child);
            } else if (child instanceof ViewGroup) {
                recycleDrawablesRecursive((ViewGroup) child, images);
            }
        }
        viewGroup.destroyDrawingCache();
    }

    // http://code.google.com/p/android/issues/detail?id=8488
    public void recycleDrawables() {
        List<ImageView> images = new ArrayList<>();
        // collect all the image views
        recycleDrawablesRecursive(this, images);
        for (ImageView imageView : images) {
            imageView.destroyDrawingCache();
            Drawable d = imageView.getDrawable();
            if (d != null && d instanceof BitmapDrawable) {
                imageView.setImageDrawable(null);
                BitmapDrawable bd = (BitmapDrawable) d;
                Bitmap bmp = bd.getBitmap();
                if (bmp != null) {
                    bmp.recycle();
                }
            }
        }
    }

    public void setFocus(Context context) {
        SoftKeyboardUtils.hideSoftKeyboard(this);
    }

    public abstract void setOnLongClickListener(OnLongClickListener l);

    /**
     * Override this to implement fling gesture suppression (e.g. for embedded WebView treatments).
     *
     * @return true if the fling gesture should be suppressed
     */
    public boolean suppressFlingGesture(MotionEvent e1, MotionEvent e2, float velocityX,
                                        float velocityY) {
        return false;
    }

    /*
     * Add a Views containing the question text, audio (if applicable), and image (if applicable).
     * To satisfy the RelativeLayout constraints, we add the audio first if it exists, then the
     * TextView to fit the rest of the space, then the image if applicable.
     */
    /*
     * Defaults to adding questionlayout to the top of the screen.
     * Overwrite to reposition.
     */
    protected void addQuestionMediaLayout(View v) {
        if (v == null) {
            Timber.e("cannot add a null view as questionMediaLayout");
            return;
        }
        // default for questionmedialayout
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        params.setMargins(10, 0, 10, 0);
        addView(v, params);
    }

    public Bundle getState() {
        return state;
    }

    public Bundle getCurrentState() {
        saveState();
        return state;
    }

    @OverridingMethodsMustInvokeSuper
    protected void saveState() {
        state = new Bundle();

        if (expanded != null) {
            state.putBoolean(GUIDANCE_EXPANDED_STATE + getFormEntryPrompt().getIndex(), expanded.get());
        }
    }

    /**
     * Add a TextView containing the help text to the default location.
     * Override to reposition.
     */
    protected void addHelpTextLayout(View v) {
        if (v == null) {
            Timber.e("cannot add a null view as helpTextView");
            return;
        }

        // default for helptext
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.BELOW, getQuestionMediaLayout().getId());
        params.setMargins(10, 0, 10, 0);
        addView(v, params);
    }

    private View createHelpTextLayout() {
        return LayoutInflater.from(getContext()).inflate(R.layout.help_text_layout, null);
    }

    private TextView setupHelpText(TextView helpText, FormEntryPrompt prompt) {
        String s = prompt.getHelpText();

        if (s != null && !s.equals("")) {
            helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getQuestionFontSize() - 3);
            // wrap to the widget of view
            helpText.setHorizontallyScrolling(false);
            helpText.setTypeface(null, Typeface.ITALIC);
            if (prompt.getLongText() == null || prompt.getLongText().isEmpty()) {
                helpText.setText(TextUtils.textToHtml(FormEntryPromptUtils.markQuestionIfIsRequired(s, prompt.isRequired())));
            } else {
                helpText.setText(TextUtils.textToHtml(s));
            }
            helpText.setTextColor(themeUtils.getPrimaryTextColor());
            helpText.setMovementMethod(LinkMovementMethod.getInstance());
            return helpText;
        } else {
            helpText.setVisibility(View.GONE);
            return helpText;
        }
    }

    /**
     * Default place to put the answer
     * (below the help text or question text if there is no help text)
     * If you have many elements, use this first
     * and use the standard addView(view, params) to place the rest
     */
    protected void addAnswerView(View v) {
        if (v == null) {
            Timber.e("cannot add a null view as an answerView");
            return;
        }
        // default place to add answer
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.addRule(RelativeLayout.BELOW, getHelpTextLayout().getId());

        params.setMargins(10, 0, 10, 0);
        addView(v, params);
    }

    /**
     * Every subclassed widget should override this, adding any views they may contain, and calling
     * super.cancelLongPress()
     */
    public void cancelLongPress() {
        super.cancelLongPress();
        if (getQuestionMediaLayout() != null) {
            getQuestionMediaLayout().cancelLongPress();
        }
        if (getHelpTextView() != null) {
            getHelpTextView().cancelLongPress();
        }
    }

    /*
     * Prompts with items must override this
     */
    public void playAllPromptText() {
        getQuestionMediaLayout().playAudio();
    }

    public void resetQuestionTextColor() {
        getQuestionMediaLayout().resetTextFormatting();
    }

    public void resetAudioButtonImage() {
        getQuestionMediaLayout().resetAudioButtonBitmap();
    }

    public void showWarning(String warningBody) {
        warningText.setVisibility(View.VISIBLE);
        warningText.setText(warningBody);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility == INVISIBLE || visibility == GONE) {
            stopAudio();
        }
    }

    public void stopAudio() {
        if (player != null && player.isPlaying()) {
            Timber.i("stopAudio " + player);
            player.stop();
            player.reset();
        }
    }

    protected Button getSimpleButton(String text, @IdRes final int withId) {
        final QuestionWidget questionWidget = this;
        final Button button = new Button(getContext());

        button.setId(withId);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getAnswerFontSize());
        button.setPadding(20, 20, 20, 20);

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);

        button.setLayoutParams(params);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Collect.allowClick()) {
                    ((ButtonWidget) questionWidget).onButtonClick(withId);
                }
            }
        });
        return button;
    }

    protected Button getSimpleButton(@IdRes int id) {
        return getSimpleButton(null, id);
    }

    protected Button getSimpleButton(String text) {
        return getSimpleButton(text, R.id.simple_button);
    }

    protected TextView getCenteredAnswerTextView() {
        TextView textView = getAnswerTextView();
        textView.setGravity(Gravity.CENTER);

        return textView;
    }

    protected TextView getAnswerTextView() {
        return getAnswerTextView("");
    }

    protected TextView getAnswerTextView(String text) {
        TextView textView = new TextView(getContext());

        textView.setId(R.id.answer_text);
        textView.setTextColor(themeUtils.getPrimaryTextColor());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, getAnswerFontSize());
        textView.setPadding(20, 20, 20, 20);
        textView.setText(text);

        return textView;
    }

    protected ImageView getAnswerImageView(Bitmap bitmap) {
        final ImageView imageView = new ImageView(getContext());
        imageView.setId(ViewIds.generateViewId());
        imageView.setPadding(10, 10, 10, 10);
        imageView.setAdjustViewBounds(true);
        imageView.setImageBitmap(bitmap);
        return imageView;
    }

    /**
     * It's needed only for external choices. Everything works well and
     * out of the box when we use internal choices instead
     */
    protected void clearNextLevelsOfCascadingSelect() {
        FormController formController = getFormController();
        if (formController == null) {
            return;
        }

        if (formController.currentCaptionPromptIsQuestion()) {
            try {
                FormIndex startFormIndex = formController.getQuestionPrompt().getIndex();
                formController.stepToNextScreenEvent();
                while (formController.currentCaptionPromptIsQuestion()
                        && formController.getQuestionPrompt().getFormElement().getAdditionalAttribute(null, "query") != null) {
                    formController.saveAnswer(formController.getQuestionPrompt().getIndex(), null);
                    formController.stepToNextScreenEvent();
                }
                formController.jumpToIndex(startFormIndex);
            } catch (JavaRosaException e) {
                Timber.d(e);
            }
        }
    }

    @Nullable
    protected FormController getFormController() {
        return ((Collect) getContext().getApplicationContext()).getFormController();
    }

    //region Accessors

    @Nullable
    public final String getInstanceFolder() {
        Collect collect = Collect.getInstance();
        if (collect == null) {
            throw new IllegalStateException("Collect application instance is null.");
        }

        FormController formController = collect.getFormController();
        if (formController == null) {
            return null;
        }

        return formController.getInstanceFile().getParent();
    }

    @NonNull
    public final ActivityLogger getActivityLogger() {
        Collect collect = Collect.getInstance();
        if (collect == null) {
            throw new IllegalStateException("Collect application instance is null.");
        }

        return collect.getActivityLogger();
    }

    public int getQuestionFontSize() {
        return questionFontSize;
    }

    public int getAnswerFontSize() {
        return questionFontSize + 2;
    }

    public TextView getGuidanceTextView() {
        return guidanceTextView;
    }

    public View getHelpTextLayout() {
        return helpTextLayout;
    }

    public MediaLayout getQuestionMediaLayout() {
        return questionMediaLayout;
    }

    public MediaPlayer getPlayer() {
        return player;
    }

    public int getPlayColor() {
        return playColor;
    }

    protected void startActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, -1);
    }

    protected Fragment getAuxFragment() {
        return ActivityResultHelper.getAuxFragment(((AppCompatActivity) getContext()), this::onActivityResultReceived);
    }

    protected void startActivityForResult(Intent intent, int requestCode, @StringRes int errorStringResource) {
        try {
            getAuxFragment().startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            if (errorStringResource != -1) {
                Toast.makeText(getContext(),
                        getContext().getString(R.string.activity_not_found, getContext().getString(errorStringResource)),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onActivityResultReceived(int requestCode, int resultCode, Intent data) {
        FormController formController = getFormController();
        if (formController == null) {
            ((FormEntryActivity) getContext()).saveToFormLoaderTask(requestCode, resultCode, data);
            return;
        }

        if (resultCode == RESULT_CANCELED || resultCode != RESULT_OK) {
            // request was canceled...
            return;
        }

        if (isResultValid(requestCode, resultCode, data)) {
            onActivityResult(requestCode, resultCode, data);
        }

        refreshCurrentView();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // to be overridden by widgets launching external intents
    }

    protected void saveAnswersForCurrentScreen() {
        ((FormEntryActivity) getContext()).saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
    }

    protected boolean isResultValid(int requestCode, int resultCode, Intent data) {
        // intent is needed for all requestCodes except of DRAW_IMAGE, ANNOTATE_IMAGE, SIGNATURE_CAPTURE, IMAGE_CAPTURE and HIERARCHY_ACTIVITY
        if (data == null &&
                requestCode != ApplicationConstants.RequestCodes.DRAW_IMAGE &&
                requestCode != ApplicationConstants.RequestCodes.ANNOTATE_IMAGE &&
                requestCode != ApplicationConstants.RequestCodes.SIGNATURE_CAPTURE &&
                requestCode != ApplicationConstants.RequestCodes.IMAGE_CAPTURE) {
            Timber.w("The intent has a null value for requestCode: %s", requestCode);
            ToastUtils.showLongToast(getContext().getString(R.string.null_intent_value));
            return false;
        }

        return resultCode == RESULT_OK;
    }

    /*
     * We have a saved image somewhere, but we really want it to be in:
     * /sdcard/odk/instances/[current instnace]/something.jpg so we move
     * it there before inserting it into the content provider. Once the
     * android image capture bug gets fixed, (read, we move on from
     * Android 1.6) we want to handle images the audio and video
     */
    protected void saveChosenImage(Uri selectedImage) {
        // Copy file to sdcard
        File instanceFile = getFormController().getInstanceFile();
        if (instanceFile != null) {
            String instanceFolder1 = instanceFile.getParent();
            String destImagePath = instanceFolder1 + File.separator + System.currentTimeMillis() + ".jpg";

            File chosenImage;
            try {
                chosenImage = MediaUtils.getFileFromUri(getContext(), selectedImage, MediaStore.Images.Media.DATA);
                if (chosenImage != null) {
                    final File newImage = new File(destImagePath);
                    FileUtils.copyFile(chosenImage, newImage);
                    ImageConverter.execute(newImage.getPath(), this, getContext());
                    ((Activity) getContext()).runOnUiThread(() -> {
                        if (this instanceof BinaryWidget) {
                            ((BinaryWidget) this).setBinaryData(newImage);
                        }
                        saveAnswersForCurrentScreen();
                    });
                } else {
                    ((Activity) getContext()).runOnUiThread(() -> {
                        Timber.e("Could not receive chosen image");
                        ToastUtils.showShortToastInMiddle(R.string.error_occured);
                    });
                }
            } catch (GDriveConnectionException e) {
                ((Activity) getContext()).runOnUiThread(() -> {
                    Timber.e("Could not receive chosen image due to connection problem");
                    ToastUtils.showLongToastInMiddle(R.string.gdrive_connection_exception);
                });
            }
        } else {
            ToastUtils.showLongToast(R.string.image_not_saved);
            Timber.w(getContext().getString(R.string.image_not_saved));
        }
    }

    /*
     * We saved the image to the tempfile_path, but we really want it to
     * be in: /sdcard/odk/instances/[current instnace]/something.jpg so
     * we move it there before inserting it into the content provider.
     * Once the android image capture bug gets fixed, (read, we move on
     * from Android 1.6) we want to handle images the audio and video
     */
    protected void saveCapturedImage() {
        // The intent is empty, but we know we saved the image to the temp file
        ImageConverter.execute(Collect.TMPFILE_PATH, this, getContext());
        File fi = new File(Collect.TMPFILE_PATH);

        //revoke permissions granted to this file due its possible usage in the camera app
        Uri uri = FileProvider.getUriForFile(getContext(),
                BuildConfig.APPLICATION_ID + ".provider",
                fi);
        FileUtils.revokeFileReadWritePermission(getContext(), uri);

        String instanceFolder = getFormController().getInstanceFile().getParent();
        String s = instanceFolder + File.separator + System.currentTimeMillis() + ".jpg";

        File nf = new File(s);
        if (!fi.renameTo(nf)) {
            Timber.e("Failed to rename %s", fi.getAbsolutePath());
        } else {
            Timber.i("Renamed %s to %s", fi.getAbsolutePath(), nf.getAbsolutePath());
        }

        if (this instanceof BinaryWidget) {
            ((BinaryWidget) this).setBinaryData(nf);
        }
        saveAnswersForCurrentScreen();
    }

    protected void saveFileAnswer(Uri media) {
        // For audio/video capture/chooser, we get the URI from the content
        // provider
        // then the widget copies the file and makes a new entry in the
        // content provider.
        if (this instanceof BinaryWidget) {
            ((BinaryWidget) this).setBinaryData(media);
        }
        saveAnswersForCurrentScreen();
        deleteMediaFromContentProvider(media);
    }

    protected void deleteMediaFromContentProvider(Uri media) {
        String filePath = MediaUtils.getDataColumn(getContext(), media, null, null);
        if (filePath != null) {
            new File(filePath).delete();
        }
        try {
            getContext().getContentResolver().delete(media, null, null);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    /*
     * Start a task to save the chosen file/audio/video with a new Thread,
     * This could support retrieving file from Google Drive.
     */
    protected void saveChosenFileInBackground(Uri media) {
        Runnable saveFileRunnable = () -> saveChosenFile(media);
        new Thread(saveFileRunnable).start();
    }

    /**
     * Save a copy of the chosen media in Collect's own path such as
     * "/storage/emulated/0/odk/instances/{form name}/filename",
     * and if it's from Google Drive and not cached yet, we'll retrieve it using network.
     * This may take a long time.
     *
     * @param selectedFile uri of the selected audio
     * @see #getFileExtensionFromUri(Uri)
     */
    private void saveChosenFile(Uri selectedFile) {
        String extension = getFileExtensionFromUri(selectedFile);
        String instanceFolder = Collect.getInstance().getFormController().getInstanceFile().getParent();
        String destPath = instanceFolder + File.separator + System.currentTimeMillis() + extension;

        try {
            File chosenFile = MediaUtils.getFileFromUri(getContext(), selectedFile, MediaStore.Images.Media.DATA);
            if (chosenFile != null) {
                final File newFile = new File(destPath);
                FileUtils.copyFile(chosenFile, newFile);
                ((Activity) getContext()).runOnUiThread(() -> {
                    if (this instanceof BinaryWidget) {
                        ((BinaryWidget) this).setBinaryData(newFile);
                    }
                    saveAnswersForCurrentScreen();
                });
            } else {
                ((Activity) getContext()).runOnUiThread(() -> {
                    Timber.e("Could not receive chosen file");
                    ToastUtils.showShortToastInMiddle(R.string.error_occured);
                });
            }
        } catch (GDriveConnectionException e) {
            ((Activity) getContext()).runOnUiThread(() -> {
                Timber.e("Could not receive chosen file due to connection problem");
                ToastUtils.showLongToastInMiddle(R.string.gdrive_connection_exception);
            });
        }
    }

    /**
     * Using contentResolver to get a file's extension by the uri returned from OnActivityResult.
     *
     * @param fileUri Whose name we want to get
     * @return The file's extension
     * @see #onActivityResult(int, int, Intent)
     * @see #saveChosenFile(Uri)
     * @see android.content.ContentResolver
     */
    private String getFileExtensionFromUri(Uri fileUri) {
        try (Cursor returnCursor = getContext().getContentResolver()
                .query(fileUri, null, null, null, null)) {
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            String filename = returnCursor.getString(nameIndex);
            // If the file's name contains extension , we cut it down for latter use (copy a new file).
            if (filename.lastIndexOf('.') != -1) {
                return filename.substring(filename.lastIndexOf('.'));
            } else {
                // I found some mp3 files' name don't contain extension, but can be played as Audio
                // So I write so, but I still there are some way to get its extension
                return "";
            }
        }
    }

    private void refreshCurrentView() {
        ((FormEntryActivity) getContext()).refreshCurrentView();
    }
}
