# Off-road image segmentation: benchmark gate, not a finished model

1. Obtain a licensed **local** image/mask dataset from the `tools/datasets/catalog.json` sources. Avoid uploading people's faces / private locations without consent. Keep large datasets outside Git.
2. Export each model's **predicted class IDs** in lossless PNG **without interpolation** (do not resize masks bilinearly). For palette masks use P mode, for 8-bit class IDs use L mode. RGB color-coded masks must be explicitly converted to IDs using the dataset's documented palette.
3. Write `manifest.json` next to relative mask files. Example:

```json
{"dataset":"my-licensed-trail-set/v1", "entries":[
 {"truth":"truth/0001.png","prediction":"preds/0001.png","positive_truth_ids":[1,4],"positive_prediction_ids":[1],"ignore_truth_ids":[255]}
]}
```

4. Run `python3 -m pip install pillow` in the local benchmark environment, then `python3 tools/vision/mask_metrics.py --manifest /path/to/manifest.json --out /path/to/metrics.json`.

Output: per-frame IoU/F1 and aggregate confusion/precision/recall, respecting ignored regions. **No model is included or executed**. The purpose is to make any future OpenCV/TFLite/NNAPI experiments comparable on *identical held-out labels*. Class IDs **must be validated per dataset** and the positive class denotes *annotated trail appearance*, not physical passability. The test does not measure energy, FPS, heating, robustness to fog/snow or GPX geographic certainty. Those require a physical device and a recorded, independently annotated field protocol.
