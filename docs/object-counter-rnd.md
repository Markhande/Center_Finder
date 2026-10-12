# Object counter: offline appearance matching

The camera captures a frame for review. OpenCV proposes complete silhouettes and splits some touching round objects. MobileNet V3 Small (MediaPipe image embedder, pinned runtime 1.0.0) extracts an appearance vector for each proposed object. Shape measurements and cosine similarity jointly determine automatic groups. Tapping a detected example searches all proposals, rather than simply selecting its automatic group.

All inference is on-device. No photo upload, network permission, API key, or runtime model download is needed. The bundled model is about 4 MB; the native MediaPipe runtime also increases APK size. Model provenance, SHA-256 and license are in app/src/main/assets/MOBILENET_NOTICE.txt and MOBILENET_LICENSE.txt.

## Review controls

- Tap a detected box to count similar items; select All to clear the example.
- Adjust Match strength to broaden or narrow appearance matching. The threshold is a heuristic, not a probability or accuracy percentage.
- Match color optionally filters by a central color histogram. Learned vectors use grayscale crops to reduce color sensitivity; grayscale does not guarantee color invariance.
- Correct count lets users exclude/restore a box or add/remove a missed center. Reviewed count reflects those edits. Changing the example, group, color policy, area, or match threshold clears edits; Reset edits also clears them.
- Drag an area and choose Count area. The example and corrections are reset because proposals change.
- Dark, overexposed or low-detail photos receive advisory messages; capture is not blocked.

## Limits and fallback

This is appearance matching on object proposals, not semantic object recognition or CountGD. It does not label a circle as a marble. Missed silhouettes cannot be recovered by the embedder, and touching items may remain merged. Plain contrasting backgrounds still matter. Overlapping, reflective, transparent, patterned or very small objects require further detector work and real-photo evaluation.

If model initialization/inference fails, the whole image consistently falls back to shape matching with a visible message. Images over 150 proposals also use this fallback to bound inference cost; no proposals are silently removed. The user can select a smaller area to enable appearance inference. Automatic clustering checks every member to avoid similarity chains joining unrelated groups.

## Benchmark plan

Keep tuning and evaluation photos separate. Collect real examples of marbles, coins, screws and mixed items, varying background, lighting, scale, rotation and overlap. Annotate target centers, group identity, visible/occluded status and the expected count. Compare baseline shape grouping against learned automatic grouping and example-guided matching on the same held-out images. Record exact-count rate, mean absolute count error, missed/duplicate detections, incorrect matches, inference latency and memory on supported physical Android devices. Do not infer real-world accuracy from synthetic test success.

Tests cover the existing counting regressions, matching rules and color policy, model loading/inference from the bundled asset, advisory photo quality and example selection/manual corrections in Compose. No real-photo benchmark dataset is included yet.
