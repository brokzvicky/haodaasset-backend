package com.vikkash.assetmanagementv1.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates a scannable QR code for each asset that encodes a deep link
 * into the frontend Asset Inventory page (?assetId=...), so scanning the
 * physical asset's QR sticker with any phone camera opens its record
 * directly — no app install required.
 *
 * Requires the following Maven dependencies (not bundled — add to pom.xml):
 *   <dependency>
 *     <groupId>com.google.zxing</groupId>
 *     <artifactId>core</artifactId>
 *     <version>3.5.3</version>
 *   </dependency>
 *   <dependency>
 *     <groupId>com.google.zxing</groupId>
 *     <artifactId>javase</artifactId>
 *     <version>3.5.3</version>
 *   </dependency>
 */
@Service
public class QrCodeService {

    private static final int SIZE = 320;

    @Value("${app.frontend.base-url:https://haodaasset.vercel.app}")
    private String frontendBaseUrl;

    /** Builds the PNG bytes for an asset's QR code. */
    public byte[] generateAssetQrCode(Long assetId, String serialNumber) throws WriterException, IOException {
        String content = frontendBaseUrl + "/assets?assetId=" + assetId
                + (serialNumber != null ? "&serial=" + serialNumber : "");

        Map<com.google.zxing.EncodeHintType, Object> hints = new EnumMap<>(com.google.zxing.EncodeHintType.class);
        hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }

    public String buildAssetDeepLink(Long assetId) {
        return frontendBaseUrl + "/assets?assetId=" + assetId;
    }
}
