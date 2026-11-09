package com.example.activity;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.example.R;
import com.example.compiler.XmlDrawableRenderer;
import com.example.util.CyberTemplates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XmlDrawableActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_xml_drawable;
    }

    @Override
    protected String getModuleTitle() {
        return "XML DRAWABLE EDITOR";
    }

    @Override
    protected String getModuleBadge() {
        return "DRAWABLE ENGINE";
    }

    @Override
    protected String getActionLabel() {
        return "RENDER DRAWABLE";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.XML_DRAWABLE_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "drawable_custom.xml";
    }

    @Override
    protected String getMimeType() {
        return "text/xml";
    }

    @Override
    protected boolean hasLivePreview() {
        return true;
    }

    @Override
    protected List<PipelineAction> getPipelineActions(String output, byte[] binary, File file) {
        List<PipelineAction> actions = new ArrayList<>();
        actions.add(new PipelineAction("📐 Open in XML Layout Editor", XmlLayoutActivity.class, null, null, false));
        return actions;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        XmlDrawableRenderer.DrawableResult res = XmlDrawableRenderer.render(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                if (containerLivePreview != null) {
                    containerLivePreview.removeAllViews();
                    View previewSurface = new View(this);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            (int) (220 * getResources().getDisplayMetrics().density),
                            (int) (140 * getResources().getDisplayMetrics().density)
                    );
                    lp.gravity = Gravity.CENTER;
                    previewSurface.setLayoutParams(lp);
                    previewSurface.setBackground(res.drawable);
                    containerLivePreview.addView(previewSurface);
                }

                String output = "XML Drawable Built Successfully.\n" + res.details;
                onExecutionSuccess(logs, "[SYS:STATUS] >> DRAWABLE RENDERED LIVE", output, null, null);
                showOutputTab(false);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
