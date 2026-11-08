package com.example.compiler;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class XmlDrawableRenderer {

    public static class DrawableResult {
        public final boolean success;
        public final Drawable drawable;
        public final String message;
        public final String details;

        public DrawableResult(boolean success, Drawable drawable, String message, String details) {
            this.success = success;
            this.drawable = drawable;
            this.message = message;
            this.details = details;
        }
    }

    public static DrawableResult render(Context context, String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new DrawableResult(false, null, "XML content is empty.", "");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
            Element root = doc.getDocumentElement();

            String rootTag = root.getTagName();
            Drawable resultDrawable = null;
            StringBuilder details = new StringBuilder();

            if ("shape".equals(rootTag)) {
                resultDrawable = parseShapeElement(context, root, details);
            } else if ("layer-list".equals(rootTag)) {
                resultDrawable = parseLayerListElement(context, root, details);
            } else if ("selector".equals(rootTag)) {
                resultDrawable = parseSelectorElement(context, root, details);
            } else {
                return new DrawableResult(false, null, "Unsupported root tag: <" + rootTag + ">. Expected <shape>, <layer-list>, or <selector>.", "");
            }

            if (resultDrawable == null) {
                return new DrawableResult(false, null, "Failed to construct drawable from XML.", "");
            }

            return new DrawableResult(true, resultDrawable, "SUCCESS: Drawable successfully built & rendered.", details.toString());

        } catch (Exception e) {
            return new DrawableResult(false, null, "XML Error: " + e.getMessage(), "");
        }
    }

    private static GradientDrawable parseShapeElement(Context context, Element shapeElem, StringBuilder details) {
        GradientDrawable gd = new GradientDrawable();

        String shapeType = getAttr(shapeElem, "shape");
        int shape = GradientDrawable.RECTANGLE;
        if ("oval".equalsIgnoreCase(shapeType)) {
            shape = GradientDrawable.OVAL;
            details.append("Shape: Oval\n");
        } else if ("line".equalsIgnoreCase(shapeType)) {
            shape = GradientDrawable.LINE;
            details.append("Shape: Line\n");
        } else if ("ring".equalsIgnoreCase(shapeType)) {
            shape = GradientDrawable.RING;
            details.append("Shape: Ring\n");
        } else {
            details.append("Shape: Rectangle\n");
        }
        gd.setShape(shape);

        NodeList children = shapeElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element elem = (Element) node;
            String tag = elem.getTagName();

            switch (tag) {
                case "solid": {
                    String colorStr = getAttr(elem, "color");
                    if (!colorStr.isEmpty()) {
                        int col = parseColor(colorStr, Color.TRANSPARENT);
                        gd.setColor(col);
                        details.append("Solid Color: ").append(colorStr).append("\n");
                    }
                    break;
                }
                case "corners": {
                    String radiusStr = getAttr(elem, "radius");
                    if (!radiusStr.isEmpty()) {
                        float r = parseDp(context, radiusStr);
                        gd.setCornerRadius(r);
                        details.append("Corner Radius: ").append(radiusStr).append("\n");
                    } else {
                        // Check individual corners
                        float tl = parseDp(context, getAttr(elem, "topLeftRadius"));
                        float tr = parseDp(context, getAttr(elem, "topRightRadius"));
                        float br = parseDp(context, getAttr(elem, "bottomRightRadius"));
                        float bl = parseDp(context, getAttr(elem, "bottomLeftRadius"));
                        gd.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
                        details.append("Custom Corner Radii (TL, TR, BR, BL)\n");
                    }
                    break;
                }
                case "stroke": {
                    String widthStr = getAttr(elem, "width");
                    String colorStr = getAttr(elem, "color");
                    int w = parseDp(context, widthStr);
                    int col = parseColor(colorStr, Color.WHITE);
                    gd.setStroke(w, col);
                    details.append("Stroke: ").append(widthStr).append(" (").append(colorStr).append(")\n");
                    break;
                }
                case "gradient": {
                    String startColor = getAttr(elem, "startColor");
                    String endColor = getAttr(elem, "endColor");
                    String centerColor = getAttr(elem, "centerColor");
                    String angleStr = getAttr(elem, "angle");

                    int sCol = parseColor(startColor, Color.TRANSPARENT);
                    int eCol = parseColor(endColor, Color.TRANSPARENT);
                    int[] colors = centerColor.isEmpty() ?
                            new int[]{sCol, eCol} :
                            new int[]{sCol, parseColor(centerColor, Color.GRAY), eCol};

                    gd.setColors(colors);
                    gd.setOrientation(parseAngle(angleStr));
                    details.append("Gradient: ").append(startColor).append(" -> ").append(endColor).append("\n");
                    break;
                }
            }
        }

        return gd;
    }

    private static Drawable parseLayerListElement(Context context, Element root, StringBuilder details) {
        NodeList children = root.getChildNodes();
        List<Drawable> layers = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element item = (Element) node;
            if ("item".equals(item.getTagName())) {
                NodeList sub = item.getChildNodes();
                for (int j = 0; j < sub.getLength(); j++) {
                    Node subNode = sub.item(j);
                    if (subNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element shape = (Element) subNode;
                        if ("shape".equals(shape.getTagName())) {
                            layers.add(parseShapeElement(context, shape, details));
                        }
                    }
                }
            }
        }
        if (layers.isEmpty()) {
            return null;
        }
        details.append("Layer List: ").append(layers.size()).append(" layer(s)\n");
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    private static Drawable parseSelectorElement(Context context, Element root, StringBuilder details) {
        // Build first item shape as representation
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element item = (Element) node;
            if ("item".equals(item.getTagName())) {
                NodeList sub = item.getChildNodes();
                for (int j = 0; j < sub.getLength(); j++) {
                    Node subNode = sub.item(j);
                    if (subNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element shape = (Element) subNode;
                        if ("shape".equals(shape.getTagName())) {
                            details.append("Selector Default State:\n");
                            return parseShapeElement(context, shape, details);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static GradientDrawable.Orientation parseAngle(String angleStr) {
        if (angleStr == null || angleStr.isEmpty()) return GradientDrawable.Orientation.LEFT_RIGHT;
        try {
            int a = Integer.parseInt(angleStr);
            switch (a) {
                case 0: return GradientDrawable.Orientation.LEFT_RIGHT;
                case 45: return GradientDrawable.Orientation.BL_TR;
                case 90: return GradientDrawable.Orientation.BOTTOM_TOP;
                case 135: return GradientDrawable.Orientation.BR_TL;
                case 180: return GradientDrawable.Orientation.RIGHT_LEFT;
                case 225: return GradientDrawable.Orientation.TR_BL;
                case 270: return GradientDrawable.Orientation.TOP_BOTTOM;
                case 315: return GradientDrawable.Orientation.TL_BR;
                default: return GradientDrawable.Orientation.LEFT_RIGHT;
            }
        } catch (Exception e) {
            return GradientDrawable.Orientation.LEFT_RIGHT;
        }
    }

    private static String getAttr(Element element, String attrName) {
        String val = element.getAttributeNS("http://schemas.android.com/apk/res/android", attrName);
        if (val.isEmpty()) {
            val = element.getAttribute("android:" + attrName);
        }
        if (val.isEmpty()) {
            val = element.getAttribute(attrName);
        }
        return val != null ? val.trim() : "";
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
