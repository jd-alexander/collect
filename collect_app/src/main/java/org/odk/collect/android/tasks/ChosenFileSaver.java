package org.odk.collect.android.tasks;

import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.helpers.ContentResolverHelper;
import org.odk.collect.android.exception.GDriveConnectionException;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.MediaUtils;

import java.io.File;
import java.lang.ref.WeakReference;

import io.reactivex.Observable;
import timber.log.Timber;

public class ChosenFileSaver {

    private final WeakReference weakReferenceContext;
    private final Uri selectedFile;
    private File newFile;
    private int errorMessageRes;

    private ChosenFileSaver(Context context, Uri selectedFile) {
        weakReferenceContext = new WeakReference<>(context);
        this.selectedFile = selectedFile;
    }

    public static Observable<SaveResult> createObservable(Context context, Uri selectedFile) {
        return Observable.create(emitter -> {
            ChosenFileSaver fileSaver = new ChosenFileSaver(context, selectedFile);
            boolean result = fileSaver.saveChosenFile();

            SaveResult saveResult = new SaveResult(result, fileSaver.getSavedFile(), fileSaver.getErrorMessageRes());
            emitter.onNext(saveResult);
            emitter.onComplete();
        });
    }

    private int getErrorMessageRes() {
        return errorMessageRes;
    }

    private File getSavedFile() {
        return newFile;
    }

    private boolean saveChosenFile() {
        Context context = (Context) weakReferenceContext.get();
        FormController formController = Collect.getInstance().getFormController();
        try {
            if (context != null && formController != null && formController.getInstanceFile() != null) {
                String extension = ContentResolverHelper.getFileExtensionFromUri(context, selectedFile);
                String instanceFolder = formController.getInstanceFile().getParent();
                String destPath = instanceFolder + File.separator + System.currentTimeMillis() + extension;

                File chosenFile = MediaUtils.getFileFromUri(context, selectedFile, MediaStore.Images.Media.DATA);
                if (chosenFile != null) {
                    newFile = new File(destPath);
                    Timber.d(FileUtils.copyFile(chosenFile, newFile));
                    return true;
                } else {
                    Timber.e("Could not receive chosen file");
                    errorMessageRes = R.string.error_occured;
                }
            } else {
                Timber.e("Could not save chosen file");
                errorMessageRes = R.string.error_occured;
            }
        } catch (GDriveConnectionException e) {
            Timber.e("Could not receive chosen file due to connection problem");
            errorMessageRes = R.string.gdrive_connection_exception;
        }
        return false;
    }

    public static class SaveResult {

        private final boolean complete;
        private final File savedFile;
        private final int errorMessageRes;

        SaveResult(boolean complete, File savedFile, int errorMessageRes) {
            this.complete = complete;
            this.savedFile = savedFile;
            this.errorMessageRes = errorMessageRes;
        }

        public boolean isComplete() {
            return complete;
        }

        public File getSavedFile() {
            return savedFile;
        }

        public int getErrorMessageRes() {
            return errorMessageRes;
        }
    }
}
