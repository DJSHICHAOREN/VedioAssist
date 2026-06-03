"""INT8 quantization to TFLite - run after export.py"""
import yaml
from pathlib import Path

def main():
    with open("config.yaml") as f:
        config = yaml.safe_load(f)
    print(f"""
TFLite quantization steps:
1. Export ONNX: python export.py
2. Convert ONNX to TFLite:
   pip install onnx-tf
   onnx-tf convert -i {config['output']['onnx_model']} -o output/ocr_saved_model
3. Generate INT8 TFLite:
   python -c "
import tensorflow as tf
converter = tf.lite.TFLiteConverter.from_saved_model('output/ocr_saved_model')
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.int8
converter.inference_output_type = tf.int8
tflite_model = converter.convert()
open('{config['output']['tflite_model']}', 'wb').write(tflite_model)
   "
4. Copy to Android: cp {config['output']['tflite_model']} ../app/src/main/assets/models/ocr_model.tflite
""")

if __name__ == "__main__":
    main()
