package org.odk.collect.android.tasks.sms.contracts;

import org.odk.collect.android.tasks.sms.models.SmsSubmission;

import java.util.Iterator;

/**
 * Contract for a component that's utilized to track sms submissions.
 */
public interface SmsSubmissionManagerContract {

    SmsSubmission getSubmissionModel(String instanceId);

    boolean markMessageAsSent(String instanceId, int messageId);

    void markMessageAsSending(String instanceId, int messageId);

    void forgetSubmission(String instanceId);

    void saveSubmission(SmsSubmission model);

    int checkNextMessageResultCode(String instanceId);

    void deferSubmissionStatus(Iterator<String> instanceIds);

    void updateMessageStatus(int resultCode, String instanceId, int messageId);
}