package org.nobi.forms.android.http.mock;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.nobi.forms.android.http.HttpCredentialsInterface;
import org.nobi.forms.android.http.HttpGetResult;

import java.net.URI;

public class MockHttpClientConnectionError extends MockHttpClientConnection {

    @Override
    @NonNull
    public HttpGetResult executeGetRequest(@NonNull URI uri, @Nullable String contentType, @Nullable HttpCredentialsInterface credentials) throws Exception {
        return null;
    }
}
