package org.nobi.forms.android.widgets;

import android.support.annotation.NonNull;

import org.javarosa.core.model.data.DecimalData;
import org.nobi.forms.android.widgets.base.RangeWidgetTest;
import org.robolectric.RuntimeEnvironment;

/**
 * @author James Knight
 */

public class RangeDecimalWidgetTest extends RangeWidgetTest<RangeDecimalWidget, DecimalData> {

    @NonNull
    @Override
    public RangeDecimalWidget createWidget() {
        return new RangeDecimalWidget(RuntimeEnvironment.application, formEntryPrompt);
    }

    @NonNull
    @Override
    public DecimalData getNextAnswer() {
        return new DecimalData(random.nextDouble());
    }
}
