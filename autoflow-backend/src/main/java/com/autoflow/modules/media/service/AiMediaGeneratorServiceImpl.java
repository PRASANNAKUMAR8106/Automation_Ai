package com.autoflow.modules.media.service;

import com.autoflow.modules.media.dto.AiMediaGenerateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiMediaGeneratorServiceImpl implements AiMediaGeneratorService {

    private final MediaStorageService mediaStorageService;

    @Override
    public MediaStorageService.MediaUploadResponse generateBrandedAsset(
            UUID orgId,
            AiMediaGenerateRequest request
    ) {
        int width = request.getWidth() > 0 ? Math.min(request.getWidth(), 2400) : 1200;
        int height = request.getHeight() > 0 ? Math.min(request.getHeight(), 2400) : 630;

        Color accentColor = parseHexColor(request.getAccentColor(), new Color(99, 102, 241)); // #6366F1
        String template = request.getTemplateType() != null ? request.getTemplateType().toUpperCase() : "COUPON_CARD";
        String headline = request.getHeadline() != null && !request.getHeadline().isBlank()
                ? request.getHeadline()
                : (request.getPrompt() != null ? request.getPrompt() : "Exclusive Offer");
        String subtext = request.getSubtext() != null ? request.getSubtext() : "Generated dynamically with AutoFlow AI";
        String badge = request.getBadgeText() != null && !request.getBadgeText().isBlank()
                ? request.getBadgeText()
                : template.replace("_", " ");

        // Interpolate any dynamic tokens if provided
        if (request.getDynamicTokens() != null) {
            for (Map.Entry<String, String> token : request.getDynamicTokens().entrySet()) {
                String placeholder = "{{" + token.getKey() + "}}";
                headline = headline.replace(placeholder, token.getValue());
                subtext = subtext.replace(placeholder, token.getValue());
                badge = badge.replace(placeholder, token.getValue());
            }
        }

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 1. Background gradient (Slate 950 to Indigo 950)
            Color bgTop = new Color(15, 23, 42); // #0F172A
            Color bgBottom = new Color(24, 24, 48);
            GradientPaint bgGradient = new GradientPaint(0, 0, bgTop, width, height, bgBottom);
            g.setPaint(bgGradient);
            g.fillRect(0, 0, width, height);

            // 2. Ambient Accent Glow in top-right
            Color glowColor = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 45);
            g.setColor(glowColor);
            g.fillOval(width - 450, -150, 600, 600);

            // 3. Central Glassmorphic Card Container
            int cardPad = (int) (width * 0.05);
            int cardW = width - (cardPad * 2);
            int cardH = height - (cardPad * 2);
            RoundRectangle2D card = new RoundRectangle2D.Float(cardPad, cardPad, cardW, cardH, 32, 32);

            g.setColor(new Color(30, 41, 59, 180)); // Slate 800 with transparency
            g.fill(card);
            g.setColor(new Color(255, 255, 255, 30));
            g.setStroke(new BasicStroke(2.0f));
            g.draw(card);

            // 4. Header Badge Pill
            int badgeX = cardPad + 40;
            int badgeY = cardPad + 45;
            g.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 50));
            g.fillRoundRect(badgeX, badgeY, 220, 40, 20, 20);
            g.setColor(accentColor);
            g.drawRoundRect(badgeX, badgeY, 220, 40, 20, 20);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(Color.WHITE);
            g.drawString("⚡ " + badge.toUpperCase(), badgeX + 16, badgeY + 25);

            // 5. Headline Typography
            int contentY = badgeY + 110;
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (height * 0.09)));
            g.setColor(Color.WHITE);
            // Truncate headline if too long for one line
            if (headline.length() > 38) {
                headline = headline.substring(0, 35) + "...";
            }
            g.drawString(headline, badgeX, contentY);

            // 6. Subtext / Description
            contentY += (int) (height * 0.08);
            g.setFont(new Font("SansSerif", Font.PLAIN, (int) (height * 0.042)));
            g.setColor(new Color(203, 213, 225)); // Slate 300
            if (subtext.length() > 65) {
                subtext = subtext.substring(0, 62) + "...";
            }
            g.drawString(subtext, badgeX, contentY);

            // 7. Dynamic Callout / Voucher Box
            int boxY = contentY + 50;
            int boxW = cardW - 80;
            int boxH = (int) (height * 0.18);
            g.setColor(new Color(15, 23, 42, 220));
            g.fillRoundRect(badgeX, boxY, boxW, boxH, 20, 20);
            g.setColor(accentColor);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(badgeX, boxY, boxW, boxH, 20, 20);

            // Voucher Details
            g.setFont(new Font("Monospaced", Font.BOLD, (int) (boxH * 0.40)));
            g.setColor(Color.WHITE);
            String voucherCode = request.getDynamicTokens() != null && request.getDynamicTokens().containsKey("code")
                    ? request.getDynamicTokens().get("code")
                    : "AUTOFLOW-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            g.drawString("CODE: " + voucherCode, badgeX + 30, boxY + (int) (boxH * 0.62));

            // Right side watermark / stamp
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(148, 163, 184)); // Slate 400
            g.drawString("VERIFIED BY AUTOFLOW AI", badgeX + boxW - 210, boxY + (int) (boxH * 0.60));

            // 8. Footer Brand Stamp
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(100, 116, 139));
            g.drawString("AutoFlow Enterprise Multi-Modal Asset Engine • Generated for Tenant " + orgId,
                    badgeX, cardPad + cardH - 24);

        } finally {
            g.dispose();
        }

        // Convert to PNG byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(canvas, "png", baos);
        } catch (IOException e) {
            log.error("Failed to render AI image to PNG: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to render AI image: " + e.getMessage(), e);
        }

        byte[] imageBytes = baos.toByteArray();
        String originalFileName = "ai_asset_" + System.currentTimeMillis() + ".png";

        return mediaStorageService.uploadFile(
                orgId,
                originalFileName,
                "image/png",
                new ByteArrayInputStream(imageBytes),
                imageBytes.length + 1024
        );
    }

    private Color parseHexColor(String hex, Color defaultColor) {
        if (hex == null || hex.isBlank()) {
            return defaultColor;
        }
        try {
            String cleanHex = hex.trim();
            if (cleanHex.startsWith("#")) {
                cleanHex = cleanHex.substring(1);
            }
            if (cleanHex.length() == 6) {
                return new Color(Integer.parseInt(cleanHex, 16));
            }
        } catch (Exception e) {
            log.debug("Invalid hex color [{}], using fallback", hex);
        }
        return defaultColor;
    }
}
