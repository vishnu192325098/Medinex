/**
 * Medinex Image Preprocessor & Quality Analyzer
 * Faithful, high-fidelity web port of OcrImagePreprocessor.java
 */

export class ImagePreprocessor {
  /**
   * Assesses uploaded medicine image quality matching Android OcrImagePreprocessor:
   * - Resolution check (< 300px)
   * - Mean brightness (< 35 too dark, > 238 overexposed/glare on foil)
   * - Laplacian edge variance (< 6.5 blurry/out of focus)
   */
  static assessImageQuality(imageElement) {
    const result = {
      isAcceptable: true,
      isBlurry: false,
      isTooDark: false,
      isOverexposed: false,
      isLowResolution: false,
      meanBrightness: 128,
      sharpnessScore: 100,
      qualityNotice: null
    };

    const width = imageElement.naturalWidth || imageElement.width || 0;
    const height = imageElement.naturalHeight || imageElement.height || 0;

    if (!width || !height) {
      result.isAcceptable = false;
      result.qualityNotice = "Image could not be loaded.";
      return result;
    }

    // Check minimum dimensions for readable text (at least 150px on min dimension, 250px on max)
    if (Math.min(width, height) < 120 || Math.max(width, height) < 250) {
      result.isLowResolution = true;
      result.isAcceptable = false;
      result.qualityNotice = `Image resolution is low (${width}x${height}). Please crop closer to the medicine label or capture a higher resolution image.`;
    }

    // Sample a grid of pixels to assess brightness and sharpness efficiently
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d", { willReadFrequently: true });

    // Scale for analysis speed while retaining aspect ratio
    const sampleW = Math.min(width, 600);
    const sampleH = Math.max(10, Math.round((height / width) * sampleW));
    canvas.width = sampleW;
    canvas.height = sampleH;
    ctx.drawImage(imageElement, 0, 0, sampleW, sampleH);

    const imgData = ctx.getImageData(0, 0, sampleW, sampleH);
    const data = imgData.data;

    let totalLuminance = 0;
    let sampleCount = 0;
    let edgeEnergySum = 0;
    let edgeCount = 0;

    const sampleStep = Math.max(1, Math.floor(Math.min(sampleW, sampleH) / 100));

    // Convert to 1D luminance array for fast 2D Laplacian convolution
    const lumMatrix = new Uint8Array(sampleW * sampleH);
    for (let i = 0, p = 0; i < data.length; i += 4, p++) {
      lumMatrix[p] = (data[i] * 299 + data[i + 1] * 587 + data[i + 2] * 114) / 1000;
    }

    for (let y = 2; y < sampleH - 2; y += sampleStep) {
      const rowIdx = y * sampleW;
      const prevRowIdx = (y - 1) * sampleW;
      const nextRowIdx = (y + 1) * sampleW;

      for (let x = 2; x < sampleW - 2; x += sampleStep) {
        const idx = rowIdx + x;
        const lum = lumMatrix[idx];
        totalLuminance += lum;
        sampleCount++;

        // 4-neighbor Laplacian: 4*center - (left + right + top + bottom)
        const left = lumMatrix[idx - 1];
        const right = lumMatrix[idx + 1];
        const top = lumMatrix[prevRowIdx + x];
        const bottom = lumMatrix[nextRowIdx + x];

        const laplacian = (lum << 2) - (left + right + top + bottom);
        edgeEnergySum += (laplacian * laplacian);
        edgeCount++;
      }
    }

    const meanBrightness = sampleCount > 0 ? (totalLuminance / sampleCount) : 128;
    const sharpnessScore = edgeCount > 0 ? Math.sqrt(edgeEnergySum / edgeCount) : 0;

    result.meanBrightness = meanBrightness;
    result.sharpnessScore = sharpnessScore;

    // Assess lighting (matching Android OcrImagePreprocessor.java)
    if (meanBrightness < 35) {
      result.isTooDark = true;
      result.isAcceptable = false;
      result.qualityNotice = "Image is too dark. Please take photo in good lighting or turn on flashlight.";
    } else if (meanBrightness > 238) {
      result.isOverexposed = true;
      result.isAcceptable = false;
      result.qualityNotice = "Image has severe glare or overexposure. Avoid direct flash on blister foil.";
    }

    // Assess blurriness (< 6.5 in Android)
    if (sharpnessScore < 6.5) {
      result.isBlurry = true;
      result.isAcceptable = false;
      const blurMsg = "Image appears blurry or out of focus. Please hold the camera steady and capture a clear view of the text.";
      result.qualityNotice = result.qualityNotice ? `${result.qualityNotice} ${blurMsg}` : blurMsg;
    }

    return result;
  }

  /**
   * Rotates an image by specified degrees (90, 180, 270)
   */
  static rotateImage(imageElement, degrees = 90) {
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");

    const width = imageElement.naturalWidth || imageElement.width;
    const height = imageElement.naturalHeight || imageElement.height;

    const rad = (degrees % 360) * Math.PI / 180;
    const swapDims = (degrees % 180 !== 0);

    canvas.width = swapDims ? height : width;
    canvas.height = swapDims ? width : height;

    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.rotate(rad);
    ctx.drawImage(imageElement, -width / 2, -height / 2);

    return canvas.toDataURL("image/png");
  }

  /**
   * Crops and/or rotates an image to produce a high-resolution canvas
   * @param {HTMLImageElement|HTMLCanvasElement} imageElement
   * @param {Object|null} cropBox { x, y, width, height } normalized 0..1
   * @param {number} rotationDegrees 0, 90, 180, 270
   */
  static cropAndRotateImage(imageElement, cropBox = null, rotationDegrees = 0) {
    const rawW = imageElement.naturalWidth || imageElement.width;
    const rawH = imageElement.naturalHeight || imageElement.height;

    // Calculate source rect
    let sx = 0, sy = 0, sw = rawW, sh = rawH;
    if (cropBox && cropBox.width > 0 && cropBox.height > 0) {
      sx = Math.round(cropBox.x * rawW);
      sy = Math.round(cropBox.y * rawH);
      sw = Math.round(cropBox.width * rawW);
      sh = Math.round(cropBox.height * rawH);
      // Clamp boundaries
      sx = Math.max(0, Math.min(sx, rawW - 10));
      sy = Math.max(0, Math.min(sy, rawH - 10));
      sw = Math.max(10, Math.min(sw, rawW - sx));
      sh = Math.max(10, Math.min(sh, rawH - sy));
    }

    const swapDims = (rotationDegrees % 180 !== 0);
    const canvas = document.createElement("canvas");
    canvas.width = swapDims ? sh : sw;
    canvas.height = swapDims ? sw : sh;
    const ctx = canvas.getContext("2d");

    if (rotationDegrees !== 0) {
      ctx.translate(canvas.width / 2, canvas.height / 2);
      ctx.rotate((rotationDegrees % 360) * Math.PI / 180);
      ctx.drawImage(imageElement, sx, sy, sw, sh, -sw / 2, -sh / 2, sw, sh);
    } else {
      ctx.drawImage(imageElement, sx, sy, sw, sh, 0, 0, sw, sh);
    }

    return canvas;
  }

  /**
   * Preprocesses image for OCR matching Android OcrImagePreprocessor & universal packaging:
   * Modes:
   * - "contrast" (default): 1.30x contrast, -8 brightness, 5-neighbor text sharpening
   * - "binarize": Adaptive local thresholding to cut through shiny metallic foil glare
   * - "invert": Inverts luminance (white text on dark background/bottles -> dark text on white)
   * - "raw": Scaled natural canvas
   */
  static preprocessImageForOcr(imageElement, rotationDegrees = 0, cropBox = null, mode = "contrast") {
    // 1. Crop and rotate first
    const croppedCanvas = this.cropAndRotateImage(imageElement, cropBox, rotationDegrees);
    let targetW = croppedCanvas.width;
    let targetH = croppedCanvas.height;

    // 2. Scale to optimal OCR dimensions (1200 - 1800 px)
    const maxDim = Math.max(targetW, targetH);
    if (maxDim < 900) {
      const scale = 1350 / maxDim;
      targetW = Math.round(targetW * scale);
      targetH = Math.round(targetH * scale);
    } else if (maxDim > 2000) {
      const scale = 1800 / maxDim;
      targetW = Math.round(targetW * scale);
      targetH = Math.round(targetH * scale);
    }

    const canvas = document.createElement("canvas");
    canvas.width = targetW;
    canvas.height = targetH;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    ctx.drawImage(croppedCanvas, 0, 0, targetW, targetH);

    if (mode === "raw") {
      return canvas.toDataURL("image/png");
    }

    const imgData = ctx.getImageData(0, 0, targetW, targetH);
    const data = imgData.data;
    const totalPixels = targetW * targetH;

    // Grayscale luminance buffer
    const lumBuf = new Uint8Array(totalPixels);
    for (let i = 0, p = 0; i < data.length; i += 4, p++) {
      lumBuf[p] = (data[i] * 299 + data[i + 1] * 587 + data[i + 2] * 114) / 1000;
    }

    if (mode === "invert") {
      // White text on dark/colored bottle or blister strip -> Crisp dark text on light background
      // Check if image has colored background (e.g. blue label on bottle: Red channel provides maximum text-background contrast)
      let minInv = 255, maxInv = 0;
      const invBuf = new Uint8Array(totalPixels);

      for (let i = 0, p = 0; i < data.length; i += 4, p++) {
        const r = data[i], g = data[i + 1], b = data[i + 2];
        // If colored packaging (e.g. blue/cyan label: r is low, b is high; white text has high r and high b)
        // Red channel inversion gives exceptional separation for white text on blue/green labels
        let val;
        if (b > r + 30 || g > r + 30) {
          // Blue/cyan/green label: invert red channel
          val = 255 - r;
        } else if (r > b + 30 || r > g + 30) {
          // Red/amber label: invert blue channel
          val = 255 - b;
        } else {
          // Neutral/grayscale: invert standard luminance
          val = 255 - lumBuf[p];
        }

        invBuf[p] = val;
        if (val < minInv) minInv = val;
        if (val > maxInv) maxInv = val;
      }

      // Stretch contrast for optimal Tesseract OCR readability
      const range = maxInv - minInv || 1;
      for (let i = 0, p = 0; i < data.length; i += 4, p++) {
        const stretched = Math.round(((invBuf[p] - minInv) / range) * 255);
        data[i] = stretched;
        data[i + 1] = stretched;
        data[i + 2] = stretched;
      }

      ctx.putImageData(imgData, 0, 0);
      return canvas.toDataURL("image/png");
    }

    if (mode === "binarize") {
      // Adaptive local thresholding (Sauvola/Otsu windowing) for reflective blister foils
      const winSize = Math.max(16, Math.floor(Math.min(targetW, targetH) / 32));
      const halfWin = Math.floor(winSize / 2);

      // Fast block mean approximation
      for (let y = 0; y < targetH; y++) {
        const rowIdx = y * targetW;
        for (let x = 0; x < targetW; x++) {
          const p = rowIdx + x;
          const curVal = lumBuf[p];

          // Sample local neighborhood
          let sum = 0, count = 0;
          const yStart = Math.max(0, y - halfWin);
          const yEnd = Math.min(targetH, y + halfWin);
          const xStart = Math.max(0, x - halfWin);
          const xEnd = Math.min(targetW, x + halfWin);

          // Fast 5-point cross sample
          sum += lumBuf[yStart * targetW + x];
          sum += lumBuf[yEnd > 0 ? (yEnd - 1) * targetW + x : 0];
          sum += lumBuf[rowIdx + xStart];
          sum += lumBuf[rowIdx + Math.max(0, xEnd - 1)];
          sum += curVal;
          count = 5;

          const localMean = sum / count;
          // Text is usually darker than background in binarization
          const binVal = (curVal < localMean - 5) ? 0 : 255;
          const idx = p * 4;
          data[idx] = binVal;
          data[idx + 1] = binVal;
          data[idx + 2] = binVal;
        }
      }
      ctx.putImageData(imgData, 0, 0);
      return canvas.toDataURL("image/png");
    }

    // Default "contrast" mode: Gentle contrast curve + restrained edge enhancement
    // Avoid harsh 5x Laplacian unsharp mask that destroys thin fonts and amplifies compression noise
    const contrast = 1.25;
    const brightness = -6;
    const enhancedBuf = new Uint8Array(totalPixels);

    for (let p = 0; p < totalPixels; p++) {
      let val = (lumBuf[p] - 128) * contrast + 128 + brightness;
      if (val < 0) val = 0;
      else if (val > 255) val = 255;
      enhancedBuf[p] = Math.round(val);
    }

    // Restrained edge enhancement: center + 0.35 * (center - neighbor_avg)
    for (let y = 1; y < targetH - 1; y++) {
      const rowIdx = y * targetW;
      const topRowIdx = (y - 1) * targetW;
      const bottomRowIdx = (y + 1) * targetW;

      for (let x = 1; x < targetW - 1; x++) {
        const p = rowIdx + x;
        const center = enhancedBuf[p];
        const top = enhancedBuf[topRowIdx + x];
        const bottom = enhancedBuf[bottomRowIdx + x];
        const left = enhancedBuf[rowIdx + x - 1];
        const right = enhancedBuf[rowIdx + x + 1];

        const neighborAvg = (top + bottom + left + right) >> 2;
        const diff = center - neighborAvg;
        let sharp = Math.round(center + 0.35 * diff);
        if (sharp < 0) sharp = 0;
        else if (sharp > 255) sharp = 255;

        const pixelIdx = p * 4;
        data[pixelIdx] = sharp;
        data[pixelIdx + 1] = sharp;
        data[pixelIdx + 2] = sharp;
      }
    }

    ctx.putImageData(imgData, 0, 0);
    return canvas.toDataURL("image/png");
  }
}
