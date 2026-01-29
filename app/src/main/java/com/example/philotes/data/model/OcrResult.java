package com.example.philotes.data.model;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * OCR识别结果
 * 包含文本内容和位置信息
 */
public class OcrResult {
    private List<TextBlock> textBlocks;
    private int imageWidth;
    private int imageHeight;

    public OcrResult(int imageWidth, int imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.textBlocks = new ArrayList<>();
    }

    public void addTextBlock(String text, Rect boundingBox, float confidence) {
        textBlocks.add(new TextBlock(text, boundingBox, confidence));
    }

    public List<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    /**
     * 将OCR结果转换为结构化文本
     * 包含位置信息，让AI能理解文本的空间布局
     */
    public String toStructuredText() {
        if (textBlocks.isEmpty()) {
            return "";
        }

        // 按从上到下、从左到右排序
        Collections.sort(textBlocks, new Comparator<TextBlock>() {
            @Override
            public int compare(TextBlock a, TextBlock b) {
                // 先按Y坐标排序（上到下）
                int yDiff = a.boundingBox.top - b.boundingBox.top;
                if (Math.abs(yDiff) > imageHeight * 0.05) { // 5%的高度差认为是不同行
                    return yDiff;
                }
                // 同一行内按X坐标排序（左到右）
                return a.boundingBox.left - b.boundingBox.left;
            }
        });

        StringBuilder result = new StringBuilder();
        result.append("=== 屏幕内容识别 ===\n");
        result.append(String.format("图片尺寸: %d x %d\n\n", imageWidth, imageHeight));

        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock block = textBlocks.get(i);
            String position = getPositionDescription(block.boundingBox);
            result.append(String.format("[%s] %s", position, block.text));

            // 添加置信度（如果较低）
            if (block.confidence < 0.7) {
                result.append(String.format(" (置信度: %.1f%%)", block.confidence * 100));
            }

            result.append("\n");
        }

        result.append("\n=== 纯文本内容 ===\n");
        for (TextBlock block : textBlocks) {
            result.append(block.text).append("\n");
        }

        return result.toString();
    }

    /**
     * 获取文本块的位置描述（9宫格）
     */
    private String getPositionDescription(Rect rect) {
        int centerX = rect.centerX();
        int centerY = rect.centerY();

        String vertical;
        if (centerY < imageHeight / 3) {
            vertical = "顶部";
        } else if (centerY < imageHeight * 2 / 3) {
            vertical = "中部";
        } else {
            vertical = "底部";
        }

        String horizontal;
        if (centerX < imageWidth / 3) {
            horizontal = "左";
        } else if (centerX < imageWidth * 2 / 3) {
            horizontal = "中";
        } else {
            horizontal = "右";
        }

        return vertical + "-" + horizontal;
    }

    /**
     * 获取简单的纯文本内容（不含位置信息）
     */
    public String getPlainText() {
        StringBuilder result = new StringBuilder();
        for (TextBlock block : textBlocks) {
            result.append(block.text).append("\n");
        }
        return result.toString().trim();
    }

    /**
     * 文本块内部类
     */
    public static class TextBlock {
        public final String text;
        public final Rect boundingBox;
        public final float confidence;

        public TextBlock(String text, Rect boundingBox, float confidence) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.confidence = confidence;
        }
    }
}
