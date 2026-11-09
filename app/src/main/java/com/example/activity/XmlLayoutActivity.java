package com.example.activity;

import com.example.R;
import com.example.compiler.XmlLayoutRenderer;
import com.example.util.CyberTemplates;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XmlLayoutActivity extends BaseModuleActivity {

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_xml_layout;
    }

    @Override
    protected String getModuleTitle() {
        return "XML LAYOUT EDITOR";
    }

    @Override
    protected String getModuleBadge() {
        return "LAYOUT ENGINE";
    }

    @Override
    protected String getActionLabel() {
        return "VALIDATE & RENDER";
    }

    @Override
    protected String getDefaultTemplate() {
        return CyberTemplates.XML_LAYOUT_SAMPLE;
    }

    @Override
    protected String getDefaultFilename() {
        return "layout_custom.xml";
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
        actions.add(new PipelineAction("🎨 Open in XML Drawable Editor", XmlDrawableActivity.class, null, null, false));
        return actions;
    }

    @Override
    protected void executeCompilation(String source, StringBuilder logs) {
        XmlLayoutRenderer.RenderResult res = XmlLayoutRenderer.render(this, source);
        mainHandler.post(() -> {
            if (res.success) {
                if (containerLivePreview != null) {
                    containerLivePreview.removeAllViews();
                    containerLivePreview.addView(res.renderedView);
                }
                String output = "XML Layout Validated Successfully.\nElement Count: " + res.elementCount + "\nRoot Type: " + res.renderedView.getClass().getSimpleName();
                onExecutionSuccess(logs, "[SYS:STATUS] >> " + res.elementCount + " VIEWS INFLATED NATIVELY", output, null, null);
                showOutputTab(false);
            } else {
                onExecutionError(logs, res.message);
            }
        });
    }
}
