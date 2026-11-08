package com.example.compiler;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class XmlLayoutRenderer {

    public static class RenderResult {
        public final boolean success;
        public final View renderedView;
        public final String message;
        public final int elementCount;

        public RenderResult(boolean success, View renderedView, String message, int elementCount) {
            this.success = success;
            this.renderedView = renderedView;
            this.message = message;
            this.elementCount = elementCount;
        }
    }

    public static RenderResult render(Context context, String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new RenderResult(false, null, "XML content is empty.", 0);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
            Element root = doc.getDocumentElement();

            int[] counter = new int[]{0};
            View rootView = buildViewNode(context, root, counter);

            if (rootView == null) {
                return new RenderResult(false, null, "Could not render root layout.", 0);
            }

            return new RenderResult(true, rootView, "SUCCESS: Valid XML Layout (" + counter[0] + " views rendered)", counter[0]);

        } catch (Exception e) {
            return new RenderResult(false, null, "XML Error: " + e.getMessage(), 0);
        }
    }

    private static View buildViewNode(Context context, Element element, int[] counter) {
        counter[0]++;
        String tagName = element.getTagName();
        // Strip namespace prefix if any (e.g. androidx.appcompat.widget.AppCompatTextView -> TextView)
        int lastDot = tagName.lastIndexOf('.');
        if (lastDot != -1) {
            tagName = tagName.substring(lastDot + 1);
        }

        View view = null;
        boolean isViewGroup = false;

        switch (tagName) {
            case "LinearLayout": {
                LinearLayout ll = new LinearLayout(context);
                String orient = getAttr(element, "orientation");
                ll.setOrientation("horizontal".equalsIgnoreCase(orient) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
                view = ll;
                isViewGroup = true;
                break;
            }
            case "RelativeLayout": {
                view = new RelativeLayout(context);
                isViewGroup = true;
                break;
            }
            case "FrameLayout": {
                view = new FrameLayout(context);
                isViewGroup = true;
                break;
            }
            case "ScrollView": {
                view = new ScrollView(context);
                isViewGroup = true;
                break;
            }
            case "TextView": {
                TextView tv = new TextView(context);
                applyTextProps(tv, element);
                view = tv;
                break;
            }
            case "Button": {
                Button btn = new Button(context);
                applyTextProps(btn, element);
                view = btn;
                break;
            }
            case "EditText": {
                EditText et = new EditText(context);
                applyTextProps(et, element);
                String hint = getAttr(element, "hint");
                if (!hint.isEmpty()) et.setHint(hint);
                view = et;
                break;
            }
            case "ImageView": {
                ImageView iv = new ImageView(context);
                iv.setImageResource(android.R.drawable.ic_menu_gallery);
                view = iv;
                break;
            }
            case "CheckBox": {
                CheckBox cb = new CheckBox(context);
                applyTextProps(cb, element);
                String checked = getAttr(element, "checked");
                cb.setChecked("true".equalsIgnoreCase(checked));
                view = cb;
                break;
            }
            case "ProgressBar": {
                ProgressBar pb = new ProgressBar(context);
                view = pb;
                break;
            }
            case "Switch":
            case "SwitchCompat":
            case "MaterialSwitch": {
                Switch sw = new Switch(context);
                applyTextProps(sw, element);
                String checked = getAttr(element, "checked");
                sw.setChecked("true".equalsIgnoreCase(checked));
                view = sw;
                break;
            }
            default: {
                // Generic container fallback
                LinearLayout fallback = new LinearLayout(context);
                fallback.setOrientation(LinearLayout.VERTICAL);
                view = fallback;
                isViewGroup = true;
                break;
            }
        }

        // Apply general attributes
        applyViewAttributes(context, view, element);

        // Recursively build children
        if (isViewGroup && view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    View childView = buildViewNode(context, (Element) node, counter);
                    if (childView != null) {
                        group.addView(childView);
                    }
                }
            }
        }

        return view;
    }

    private static void applyViewAttributes(Context context, View view, Element element) {
        String widthStr = getAttr(element, "layout_width");
        String heightStr = getAttr(element, "layout_height");

        int width = parseDimension(context, widthStr, ViewGroup.LayoutParams.MATCH_PARENT);
        int height = parseDimension(context, heightStr, ViewGroup.LayoutParams.WRAP_CONTENT);

        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(width, height);
        view.setLayoutParams(lp);

        // Padding
        String padStr = getAttr(element, "padding");
        if (!padStr.isEmpty()) {
            int p = parseDp(context, padStr);
            view.setPadding(p, p, p, p);
        }

        // Background color
        String bgStr = getAttr(element, "background");
        if (!bgStr.isEmpty()) {
            int color = parseColor(bgStr, Color.TRANSPARENT);
            view.setBackgroundColor(color);
        }
    }

    private static void applyTextProps(TextView tv, Element element) {
        String text = getAttr(element, "text");
        if (!text.isEmpty()) {
            tv.setText(text);
        }
        String colorStr = getAttr(element, "textColor");
        if (!colorStr.isEmpty()) {
            tv.setTextColor(parseColor(colorStr, Color.WHITE));
        } else {
            tv.setTextColor(Color.WHITE);
        }
        String sizeStr = getAttr(element, "textSize");
        if (!sizeStr.isEmpty()) {
            float sp = parseSp(sizeStr);
            if (sp > 0) tv.setTextSize(sp);
        }
        String textStyle = getAttr(element, "textStyle");
        if ("bold".equalsIgnoreCase(textStyle)) {
            tv.setTypeface(null, Typeface.BOLD);
        } else if ("italic".equalsIgnoreCase(textStyle)) {
            tv.setTypeface(null, Typeface.ITALIC);
        }
        String gravity = getAttr(element, "gravity");
        if ("center".equalsIgnoreCase(gravity)) {
            tv.setGravity(Gravity.CENTER);
        } else if ("center_horizontal".equalsIgnoreCase(gravity)) {
            tv.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }

    private static String getAttr(Element element, String attrName) {
        // Try with android namespace
        String val = element.getAttributeNS("http://schemas.android.com/apk/res/android", attrName);
        if (val.isEmpty()) {
            val = element.getAttribute("android:" + attrName);
        }
        if (val.isEmpty()) {
            val = element.getAttribute(attrName);
        }
        return val != null ? val.trim() : "";
    }

    private static int parseDimension(Context context, String dim, int defaultValue) {
        if ("match_parent".equalsIgnoreCase(dim) || "fill_parent".equalsIgnoreCase(dim)) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        if ("wrap_content".equalsIgnoreCase(dim)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        int dp = parseDp(context, dim);
        return dp > 0 ? dp : defaultValue;
    }

    private static int parseDp(Context context, String val) {
        if (val == null || val.isEmpty()) return 0;
        try {
            String num = val.replaceAll("[^0-9.]", "");
            float f = Float.parseFloat(num);
            return (int) (f * context.getResources().getDisplayMetrics().density);
        } catch (Exception e) {
            return 0;
        }
    }

    private static float parseSp(String val) {
        if (val == null || val.isEmpty()) return 14f;
        try {
            String num = val.replaceAll("[^0-9.]", "");
            return Float.parseFloat(num);
        } catch (Exception e) {
            return 14f;
        }
    }

    private static int parseColor(String str, int defaultColor) {
        if (str == null || str.isEmpty()) return defaultColor;
        try {
            if (str.startsWith("#")) {
                return Color.parseColor(str);
            }
            if ("@android:color/white".equalsIgnoreCase(str)) return Color.WHITE;
            if ("@android:color/black".equalsIgnoreCase(str)) return Color.BLACK;
            if ("@android:color/transparent".equalsIgnoreCase(str)) return Color.TRANSPARENT;
            return Color.parseColor("#" + str);
        } catch (Exception e) {
            return defaultColor;
        }
    }
}
